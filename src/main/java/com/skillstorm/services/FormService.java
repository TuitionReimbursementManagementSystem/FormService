package com.skillstorm.services;

import com.skillstorm.constants.EventType;
import com.skillstorm.constants.GradeFormat;
import com.skillstorm.dtos.FormDto;
import com.skillstorm.dtos.DownloadResponseDto;
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

    // Update Form by ID:
    Mono<FormDto> updateById(UUID id, FormDto updatedForm);

    // Delete by ID:
    Mono<Void> deleteById(UUID id);

    // Get all EventTypes:
    Flux<EventType> getEventTypes();

    // Get all GradeFormats:
    Flux<GradeFormat> getGradingFormats();

    // Upload Event attachment to S3:
    Mono<FormDto> uploadEventAttachment(UUID id, String contentType, byte[] attachment);

    // Upload Supervisor pre-approval attachment to S3:
    Mono<FormDto> uploadSupervisorAttachment(UUID id, String contentType, byte[] attachment);

    // Upload Department Head pre-approval attachment to S3:
    Mono<FormDto> uploadDepartmentHeadAttachment(UUID id, String contentType, byte[] attachment);

    // Download Event attachment from S3:
    Mono<DownloadResponseDto> downloadEventAttachment(UUID id);

    // Download Supervisor attachment from S3:
    Mono<DownloadResponseDto> downloadSupervisorAttachment(UUID id);

    // Download Department Head attachment from S3:
    Mono<DownloadResponseDto> downloadDepartmentHeadAttachment(UUID id);

    // Submit Form for Supervisor Approval:
    Mono<FormDto> submitForApproval(UUID id, String username);

    // Submit Form for Department Head approval:
    Mono<FormDto> supervisorApprove(UUID id, String username);

    // Submit Form for Benco approval:
    Mono<FormDto> departmentHeadApprove(UUID id, String username);

    // Deny Request Form:
    Mono<FormDto> denyRequest(UUID id, String reason);

    // Approve Request Form:
    Mono<FormDto> bencoApprove(UUID id);

    // Awards the reimbursement after satisfactory completion of event:
    Mono<FormDto> awardReimbursement(UUID id);

    // Cancel a Reimbursement Request:
    Mono<Void> cancelRequest(UUID id);
}
