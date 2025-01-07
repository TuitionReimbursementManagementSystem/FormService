package com.skillstorm.services;

import com.skillstorm.constants.*;
import com.skillstorm.dtos.*;
import com.skillstorm.exceptions.FormNotFoundException;
import com.skillstorm.exceptions.InsufficientNoticeException;
import com.skillstorm.exceptions.RequestAlreadyAwardedException;
import com.skillstorm.exceptions.UnsupportedFileTypeException;
import com.skillstorm.repositories.FormRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FormServiceImpl implements FormService {

    private final FormRepository formRepository;
    private final S3Service s3Service;
    private final RabbitTemplate rabbitTemplate;
    private final Map<String, MonoSink<UserDto>> lookupCorrelationMap;
    private final Map<String, MonoSink<ReimbursementMessageDto>> reimbursementCorrelationMap;

    @Autowired
    public FormServiceImpl(FormRepository formRepository, S3Service s3Service, RabbitTemplate rabbitTemplate) {
        this.formRepository = formRepository;
        this.s3Service =s3Service;
        this.rabbitTemplate = rabbitTemplate;
        this.lookupCorrelationMap = new ConcurrentHashMap<>();
        this.reimbursementCorrelationMap = new ConcurrentHashMap<>();
    }

    // Create new Form. Verify event start date is at least a week from today:
    @Override
    public Mono<FormDto> createForm(FormDto newForm) {
        LocalDate eventDate = LocalDate.parse(newForm.getDate());
        return (eventDate.minusDays(7).isBefore(LocalDate.now())) ?
                Mono.error(new InsufficientNoticeException("notice.not.sufficient")) :
                formRepository.save(newForm.mapToEntity())
                        .map(FormDto::new);
    }

    // Find Form by ID:
    @Override
    public Mono<FormDto> findById(UUID id) {
        return formRepository.findById(id)
                .map(FormDto::new)
                .switchIfEmpty(Mono.error(new FormNotFoundException("form.not.found", id)));
    }

    // Find all Forms:
    @Override
    public Flux<FormDto> findAll() {
        return formRepository.findAll()
                .map(FormDto::new);
    }

    // Find all active forms for a given User. Filter by Status:
    // TODO: Consider denormalizing data into a separate table to query forms by username. For now we'll just include username as
    // TODO: a Clustering Key so that at least we're searching across partitions using sorted data. Will filter by status using Java since
    // TODO: result set for a single User will always be small
    @Override
    public Flux<FormDto> findAllFormsByUsernameAndStatus(String username, String status) {
        return formRepository.findAllFormsByUsername(username)
                .filter(form -> status == null || status.equalsIgnoreCase(form.getStatus().toString()))
                .map(FormDto::new);
    }

    // Update Form by ID:
    @Override
    public Mono<FormDto> updateById(UUID id, FormDto updatedForm) {
        return findById(id).flatMap(existingForm -> {
            // Set the read-only fields that we don't want the user changing in an edit:
            updatedForm.setId(id);
            updatedForm.setAttachment(existingForm.getAttachment());
            updatedForm.setSupervisorAttachment(existingForm.getSupervisorAttachment());
            updatedForm.setDepartmentHeadAttachment(existingForm.getDepartmentHeadAttachment());
            updatedForm.setStatus(existingForm.getStatus());
            updatedForm.setReasonDenied(existingForm.getReasonDenied());
            updatedForm.setExcessFundsApproved(existingForm.isExcessFundsApproved());
            updatedForm.setReimbursement(existingForm.getReimbursement());
            return formRepository.save(updatedForm.mapToEntity())
                    .map(FormDto::new);
        });
    }

    // Delete Form by ID:
    // TODO: Check for existence prior to deleting
    @Override
    public Mono<Void> deleteById(UUID id) {
        return formRepository.deleteById(id);
    }

    // Get all Event Types:
    @Override
    public Flux<EventType> getEventTypes() {
        return Flux.fromArray(EventType.values());
    }

    // Get all GradeFormats:
    @Override
    public Flux<GradeFormat> getGradingFormats() {
        return Flux.fromArray(GradeFormat.values());
    }

    // Get all Statuses:
    @Override
    public Flux<Status> getAllStatuses() {
        return Flux.fromArray(Status.values());
    }

    // Submit Form for Supervisor Approval:
    @Override
    public Mono<FormDto> submitForApproval(UUID id, String username) {
        // Pull the Form from the database and get the user's supervisor from the User-Service:
        return findById(id).flatMap(formDto -> getApprover(username, Queues.SUPERVISOR_LOOKUP, Queues.SUPERVISOR_RESPONSE)
                .flatMap(supervisor -> {
                    // If Form contains Supervisor pre-approval or if the Supervisor is also a Department Head, skip Supervisor approval step:
                    if (formDto.getSupervisorAttachment() != null || "DEPARTMENT_HEAD".equalsIgnoreCase(supervisor.getRole())) {
                        return supervisorApprove(id, supervisor.getUsername());
                    }

                    // Otherwise, submit to Supervisor for approval:
                    formDto.setStatus(Status.AWAITING_SUPERVISOR_APPROVAL);
                    return sendRequestForApproval(id, supervisor.getUsername())
                            .then(formRepository.save(formDto.mapToEntity())
                                    .map(FormDto::new));
                }));
    }

    // Supervisor approve request:
    @Override
    public Mono<FormDto> supervisorApprove(UUID id, String supervisor) {
        return findById(id)
                .flatMap(formDto -> {
                    if(formDto.getDepartmentHeadAttachment() != null) {
                        return departmentHeadApprove(id,  supervisor);
                    }

                    formDto.setStatus(Status.AWAITING_DEPARTMENT_HEAD_APPROVAL);
                    return getApprover(supervisor, Queues.DEPARTMENT_HEAD_LOOKUP, Queues.DEPARTMENT_HEAD_RESPONSE)
                            .flatMap(departmentHead -> sendRequestForApproval(id, departmentHead.getUsername()))
                            .then(removeRequestFromInbox(id, supervisor))
                            .then(formRepository.save(formDto.mapToEntity()))
                            .map(FormDto::new);
                });
    }

    // Department Head approve request. Again the argument passed here may not be the actual Department Head
    // but so long as the Benco is responsible for a Department it would resolve the same:
    @Override
    public Mono<FormDto> departmentHeadApprove(UUID id, String departmentHead) {
        return findById(id)
                .flatMap(formDto -> {
                    formDto.setStatus(Status.AWAITING_BENCO_APPROVAL);
                    return getApprover(departmentHead, Queues.BENCO_LOOKUP, Queues.BENCO_RESPONSE)
                            .flatMap(benco -> sendRequestForApproval(id, benco.getUsername()))
                            .then(formRepository.save(formDto.mapToEntity()))
                            .map(FormDto::new);
                });
    }

    // Deny Request Form:
    @Override
    public Mono<FormDto> denyRequest(UUID id, String reason) {
        return findById(id).flatMap(formDto -> {
            formDto.setStatus(Status.DENIED);
            formDto.setReasonDenied(reason);
            return sendRequestForApproval(id, formDto.getUsername())
                    .then(formRepository.save(formDto.mapToEntity()))
                    .map(FormDto::new);
        });
    }

    // Benco approve request:
    @Override
    public Mono<FormDto> bencoApprove(UUID id) {
        return findById(id).flatMap(formDto -> sendRequestForApproval(id, formDto.getUsername())
                .then(getAdjustedReimbursement(formDto.getUsername(), formDto.getReimbursement()))
                .flatMap(adjustedReimbursement -> {
                    formDto.setStatus(Status.PENDING);
                    formDto.setReimbursement(adjustedReimbursement.getReimbursement());
                    return formRepository.save(formDto.mapToEntity());
                }).map(FormDto::new));
    }

    // Awards the reimbursement after satisfactory completion of event:
    @Override
    public Mono<FormDto> awardReimbursement(UUID id) {
        return findById(id).flatMap(formDto -> {
            formDto.setStatus(Status.APPROVED);
            return sendRequestForApproval(id, formDto.getUsername())
                    .then(formRepository.save(formDto.mapToEntity()))
                    .map(FormDto::new);
        });
    }

    // Cancel a Reimbursement Request:
    @Override
    public Mono<Void> cancelRequest(UUID id) {
        return findById(id).flatMap(form -> {
            // If it has already been approved, it cannot be canceled. May also decide to disable ability to cancel requests that have been explicitly denied for record keeping purposes:
            if("APPROVED".equalsIgnoreCase(form.getStatus().name())) {
                return Mono.error(new RequestAlreadyAwardedException("request.already.awarded"));
            }
            // If it is not Pending then no adjustments to User's allowance are necessary, so we can just delete the entry:
            if(!"PENDING".equalsIgnoreCase(form.getStatus().name())) {
                return formRepository.deleteById(id);
            }
            // Otherwise, we need to return the pending amount to the User's allowance. May also need to find all currently Pending forms for the User and re-run them to utilize the newly available funds:
            ReimbursementMessageDto reimbursementMessage = new ReimbursementMessageDto(form.getUsername(), form.getReimbursement());
            return sendCancellationMessage(reimbursementMessage)
                    .then(formRepository.deleteById(id));
        });
    }

    private Mono<Void> sendCompletionVerificationRequest(UUID id, String approver) {
        ApprovalRequestDto approvalRequest = new ApprovalRequestDto(id, approver);
        return Mono.fromRunnable(() -> rabbitTemplate.convertAndSend(Queues.COMPLETION_VERIFICATION.toString(), approver));
    }

    // Send message to User-Service to restore balance from Pending form being cancelled by the User. If status is other than Pending no cross-service communication
    //  is needed to cancel a request:
    private Mono<Void> sendCancellationMessage(ReimbursementMessageDto reimbursementMessage) {
        return Mono.fromRunnable(() -> rabbitTemplate.convertAndSend(Queues.CANCEL_REQUEST.toString(), reimbursementMessage));
    }

    // Send a request to the User-Service to look up an approver based on the employee's username (direct supervisor, department head, benco):
    private Mono<UserDto> getApprover(String username, Queues lookupQueue, Queues responseQueue) {
        return Mono.create(sink -> {
            String correlationId = UUID.randomUUID().toString();

            // Put the sink into the correlation map for later response handling
            lookupCorrelationMap.put(correlationId, sink);

            // Set up the RabbitMQ message to send
            rabbitTemplate.convertAndSend(lookupQueue.toString(), username, message -> {
                message.getMessageProperties().setCorrelationId(correlationId);
                message.getMessageProperties().setReplyTo(responseQueue.toString());
                return message;
            });
        });
    }

    // Return approver to getApprover:
    @RabbitListener(queues = {"user-response-queue", "supervisor-response-queue", "department-head-response-queue", "benco-response-queue"})
    public Mono<Void> awaitApproverResponse(@Payload UserDto approver, @Header(AmqpHeaders.CORRELATION_ID) String correlationId) {
        MonoSink<UserDto> sink = lookupCorrelationMap.remove(correlationId);
        if(sink != null) {
            sink.success(approver);
        }
        return Mono.empty();
    }

    // Send a message to User-Service to update User's yearly allowance to reflect the value of the approved Form
    private Mono<ReimbursementMessageDto> getAdjustedReimbursement(String username, BigDecimal reimbursement) {
        return Mono.create(sink -> {
            String correlationId = UUID.randomUUID().toString();
            reimbursementCorrelationMap.put(correlationId, sink);

            ReimbursementMessageDto reimbursementData = new ReimbursementMessageDto(username, reimbursement);
            rabbitTemplate.convertAndSend(Queues.ADJUSTMENT_REQUEST.toString(), reimbursementData, message -> {
                message.getMessageProperties().setCorrelationId(correlationId);
                message.getMessageProperties().setReplyTo(Queues.ADJUSTMENT_RESPONSE.toString());
                return message;
            });
        });
    }

    // Returns an adjusted amount to account for the fact that User's allowance may not fully cover the amount on the Form:
    @RabbitListener(queues = "adjustment-response-queue")
    public Mono<Void> awaitAdjustmentResponse(@Payload ReimbursementMessageDto adjustedReimbursement, @Header(AmqpHeaders.CORRELATION_ID) String correlationId) {
        MonoSink<ReimbursementMessageDto> sink = reimbursementCorrelationMap.remove(correlationId);
        if(sink != null) {
            sink.success(adjustedReimbursement);
        }
        return Mono.empty();
    }

    // Send ApprovalRequest to an approver's inbox:
    private Mono<Void> sendRequestForApproval(UUID formId, String username) {
        ApprovalRequestDto approvalRequest = new ApprovalRequestDto(formId, username.toLowerCase());
        return Mono.fromRunnable(() -> rabbitTemplate.convertAndSend(Queues.APPROVAL_REQUEST.toString(), approvalRequest));
    }

    // Send DeletionRequest to clear message from User's inbox:
    private Mono<Void> removeRequestFromInbox(UUID formId, String username) {
        ApprovalRequestDto approvalRequest = new ApprovalRequestDto(formId, username.toLowerCase());
        return Mono.fromRunnable(() -> rabbitTemplate.convertAndSend(Queues.DELETION_REQUEST.toString(), approvalRequest));
    }

    // Handle automatic approvals:
    // TODO: Currently will not execute without explicitly subscribing. Should revise later to something that follows best practice:
    @RabbitListener(queues = "automatic-approval-queue")
    public Mono<Void> handleAutomaticApproval(@Payload ApprovalRequestDto approvalRequest) {
        return getApprover(approvalRequest.getUsername(), Queues.USER_LOOKUP, Queues.USER_RESPONSE)
                .map(user -> {
                    // Username and role should correspond to the user who was supposed to approve the request:
                    String username = user.getUsername().toLowerCase();
                    String role = user.getRole().toLowerCase();
                    UUID formId = approvalRequest.getFormId();

                    switch(role) {
                        case "benco" -> {
                            return sendEscalationEmail(username)
                                    .subscribe();
                        }
                        case "department_head" -> {
                            return departmentHeadApprove(formId, username)
                                    .subscribe();
                        }
                        default -> {
                            return supervisorApprove(formId, username)
                                    .subscribe();
                        }
                    }
                }).then();
    }

    // TODO: Actual email implementation may be beyond scope of the project, but will eventually
    // TODO: look up Benco's supervisor and send message to MessageService:
    private Mono<Void> sendEscalationEmail(String username) {
        return Mono.empty();
    }

    // Generate a pre-signed URL to allow user to upload file directly to S3 from their own machine:
    @Override
    public Mono<UploadUrlResponse> generateUploadUrl(UUID formId, String contentType, AttachmentType attachmentType) {
        // Verify file type is appropriate for the type of attachment:
        if(!isValidContentType(contentType, attachmentType)) {
            return Mono.error(new UnsupportedFileTypeException("Invalid content type"));
        }

        // Generate a unique key for the object to be stored in S3:
        String key = String.format("%s/%s", formId, attachmentType.name().toLowerCase());

        // Generate the pre-signed url and send both it and the key back in the response:
        return s3Service.generateUploadUrl(key, contentType)
                .map(url -> new UploadUrlResponse(url, key));
    }

    // Method to verify user file is acceptable content type:
    private boolean isValidContentType(String contentType, AttachmentType attachmentType) {
        return switch (attachmentType) {
            case EVENT -> Set.of("application/pdf", "image/png", "image/jpg", "image/jpeg", "text/plain",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                            .contains(contentType);

            case SUPERVISOR_APPROVAL, DEPARTMENT_HEAD_APPROVAL ->
                    "application/vnd.ms-outlook".equalsIgnoreCase(contentType);

            case PROOF_OF_COMPLETION -> Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.openxmlformats-officedocument.presentationml.slideshow")
                    .contains(contentType);
        };
    }

    // Use the attachment type to set the appropriate attachment field with the file's s3 bucket key:
    @Override
    public Mono<AttachmentUpdateDto> updateAttachmentField(UUID id, AttachmentType attachmentType, String key) {
        return findById(id).flatMap(formDto -> {
            String attachmentName = switch (attachmentType) {
                case EVENT -> {
                    formDto.setAttachment(key);
                    yield "attachment";
                }
                case SUPERVISOR_APPROVAL -> {
                    formDto.setSupervisorAttachment(key);
                    yield "supervisorAttachment";
                }
                case DEPARTMENT_HEAD_APPROVAL -> {
                    formDto.setDepartmentHeadAttachment(key);
                    yield "departmentHeadAttachment";
                }
                case PROOF_OF_COMPLETION -> {
                    formDto.setCompletionAttachment(key);
                    yield "completionAttachment";
                }
            };
            return formRepository.save(formDto.mapToEntity())
                    .thenReturn(new AttachmentUpdateDto(attachmentName, key));
        });
    }

    // Generate a pre-signed URL to allow user to download file from S3:
    @Override
    public Mono<String> generateDownloadUrl(UUID id, AttachmentType attachmentType) {
        return findById(id).flatMap(formDto -> switch(attachmentType) {
            case EVENT -> s3Service.generateDownloadUrl(formDto.getAttachment());
            case SUPERVISOR_APPROVAL -> s3Service.generateDownloadUrl(formDto.getSupervisorAttachment());
            case DEPARTMENT_HEAD_APPROVAL -> s3Service.generateDownloadUrl(formDto.getDepartmentHeadAttachment());
            case PROOF_OF_COMPLETION -> s3Service.generateDownloadUrl(formDto.getCompletionAttachment());
        });
    }
}
