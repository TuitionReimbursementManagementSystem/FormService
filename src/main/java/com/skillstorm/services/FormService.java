package com.skillstorm.services;

import com.skillstorm.constants.AttachmentType;
import com.skillstorm.constants.EventType;
import com.skillstorm.constants.GradeFormat;
import com.skillstorm.constants.Status;
import com.skillstorm.dtos.AttachmentUpdateDto;
import com.skillstorm.dtos.DenialDto;
import com.skillstorm.dtos.FormDto;
import com.skillstorm.dtos.UploadUrlResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FormService {

    // Create new Form:
    Mono<FormDto> createForm(FormDto newForm);

    // Find Form by ID:
    Mono<FormDto> findById(UUID id);

    // Find all Forms:
    Flux<FormDto> findAll();

    // Find all active forms for a given User. Filter by Status:
    Flux<FormDto> findAllFormsByUsernameAndStatus(String username, String status);

    // Update Form by ID:
    Mono<FormDto> updateById(UUID id, FormDto updatedForm);

    // Delete by ID:
    Mono<Void> deleteById(UUID id);

    // Get all EventTypes:
    Flux<EventType> getEventTypes();

    // Get all GradeFormats:
    Flux<GradeFormat> getGradingFormats();

    // Get all Statuses:
    Flux<Status> getAllStatuses();

    // Submit Form for Supervisor Approval:
    Mono<FormDto> submitForApproval(UUID id, String username);

    // Supervisor approves request:
    Mono<FormDto> supervisorApprove(UUID id, String supervisor);

    // Department Head approves request:
    Mono<FormDto> departmentHeadApprove(UUID id, String departmentHead);

    // Benco approves request:
    Mono<FormDto> bencoApprove(UUID id);

    // Awards the reimbursement after satisfactory completion of event:
    Mono<FormDto> awardReimbursement(UUID id);

    // Cancel a Reimbursement Request:
    Mono<Void> cancelRequest(UUID id);

    // Deny Request Form:
    Mono<FormDto> denyRequest(UUID id, DenialDto denialDto);

    // Generate a pre-signed URL to allow user to upload file directly to S3 from their own machine:
    Mono<UploadUrlResponse> generateUploadUrl(UUID formId, String contentType, AttachmentType attachmentType);

    // // Update the Form's attachment fields after a successful upload:
    Mono<AttachmentUpdateDto> updateAttachmentField(UUID id, AttachmentType attachmentType, String key);

    // Generate a pre-signed URL to allow user to download file from S3:
    Mono<String> generateDownloadUrl(UUID id, AttachmentType attachmentType);
}
