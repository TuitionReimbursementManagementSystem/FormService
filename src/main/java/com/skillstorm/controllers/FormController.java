package com.skillstorm.controllers;

import com.skillstorm.constants.AttachmentType;
import com.skillstorm.constants.EventType;
import com.skillstorm.constants.GradeFormat;
import com.skillstorm.constants.Status;
import com.skillstorm.dtos.DenialDto;
import com.skillstorm.dtos.FormDto;
import com.skillstorm.dtos.UploadUrlResponse;
import com.skillstorm.services.FormService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/forms")
public class FormController {

    private final FormService formService;

    @Autowired
    public FormController(FormService formService) {
        this.formService = formService;
    }

    // Test endpoint:
    @GetMapping("/hello")
    public Mono<String> hello() {
        return Mono.just("Hello Form Service");
    }

    // Create new Form:
    @PostMapping
    public Mono<FormDto> createForm(@RequestBody FormDto newForm) {
        return formService.createForm(newForm);
    }

    // Find Form by ID:
    @GetMapping("/{id}")
    public Mono<FormDto> findById(@PathVariable("id") UUID id) {
        return formService.findById(id);
    }

    // Find all Forms. Test utility for now, but may eventually return all Forms created by a specific User:
    @GetMapping
    public Flux<FormDto> findAll() {
        return formService.findAll();
    }

    // View all of a User's Forms. Optionally filter by status:
    @GetMapping("/active")
    public Flux<FormDto> findAllFormsByUsernameAndStatus(@RequestParam(value = "status", required = false) String status, @RequestHeader("username") String username) {
        return formService.findAllFormsByUsernameAndStatus(username, status);
    }

    // Update Form by ID:
    @PutMapping("/{id}")
    public Mono<FormDto> updateById(@PathVariable("id") UUID id, @Valid @RequestBody FormDto updatedForm) {
        return formService.updateById(id, updatedForm);
    }

    // Delete Form by ID:
    @DeleteMapping("/{id}")
    public Mono<Void> deleteById(@PathVariable("id") UUID id) {
        return formService.deleteById(id);
    }

    // Get EventTypes. Used to populate a list for the user to choose from:
    @GetMapping("/events")
    public Flux<EventType> getEventTypes() {
        return formService.getEventTypes();
    }

    // Get GradeFormats. Used to populate a list for the user to choose from:
    @GetMapping("/grade-formats")
    public Flux<GradeFormat> getGradingFormats() {
        return formService.getGradingFormats();
    }

    // Get Status types. Used to populate a list for the User to use as a filter:
    @GetMapping("statuses")
    public Flux<Status> getAllStatuses() {
        return formService.getAllStatuses();
    }

    // Submit Form for Approval:
    @PutMapping("/{id}/submit")
    public Mono<FormDto> submit(@PathVariable("id") UUID id, @RequestHeader("username") String username) {
        return formService.submitForApproval(id, username);
    }

    // Supervisor approve request:
    @PutMapping("/{id}/supervisor-approve")
    public Mono<FormDto> supervisorApproval(@PathVariable("id") UUID id, @RequestHeader("username") String supervisor) {
        return formService.supervisorApprove(id, supervisor);
    }

    // Department Head approve request:
    @PutMapping("/{id}/department-head-approve")
    public Mono<FormDto> departmentHeadApproval(@PathVariable("id") UUID id, @RequestHeader("username") String departmentHead) {
        return formService.departmentHeadApprove(id, departmentHead);
    }

    // Deny the reimbursement request:
    // TODO: 'Approver' username in header?
    @PutMapping("/{id}/deny")
    public Mono<FormDto> denyRequest(@PathVariable("id") UUID id, @Valid @RequestBody DenialDto denialDto) {
        return formService.denyRequest(id, denialDto.getReason());
    }

    // Benco approve request. Still pending and requires passing grade / presentation to be granted:
    // TODO: Verify that approver is a Benco either here or in service method:
    @PutMapping("/{id}/benco-approve")
    public Mono<FormDto> bencoApproval(@PathVariable("id") UUID id, @RequestHeader("username") String benco) {
        return formService.bencoApprove(id);
    }

    // Awards the reimbursement to the User after satisfactory completion of the event:
    // TODO: Verify that the approver is either a Benco or a Department Head
    // TODO: Consider recalculating reimbursement amount prior to finishing as a simpler means of handling the canceled Pending form scenario
    @PutMapping("/{id}/award-reimbursement")
    public Mono<FormDto> awardReimbursement(@PathVariable("id") UUID id, @RequestHeader("username") String approver) {
        return formService.awardReimbursement(id);
    }

    // Cancel a Reimbursement request. Only if has not yet been awarded:
    //TODO: If Form was Pending, Recalculate other Pending forms to utilize any freed Reimbursement allowance?
    @DeleteMapping("/{id}/cancel")
    public Mono<Void> cancelRequest(@PathVariable("id") UUID id) {
        return formService.cancelRequest(id);
    }

    // Generate a Pre-signed Url to allow user to upload file attachments to S3:
    @PostMapping("/{id}/attachments/url")
    public Mono<UploadUrlResponse> generateUploadUrl(@PathVariable("id") UUID id, @RequestParam String contentType,
                                                     @RequestParam AttachmentType attachmentType) {
        return formService.generateUploadUrl(id,contentType, attachmentType);
    }

    // Update the Form's attachment fields after a successful upload:
    @PutMapping("/{id}/attachments/url")
    public Mono<FormDto> updateAttachmentField(@PathVariable("id") UUID id, @RequestParam AttachmentType attachmentType, @RequestParam String key) {
        return formService.updateAttachmentField(id, attachmentType, key);
    }

    // Generate a Pre-signed Url to allow user to download file attachments from S3:
    @GetMapping("/{id}/attachments/url")
    public Mono<String> generateDownloadUrl(@PathVariable("id") UUID id, @RequestParam AttachmentType attachmentType) {
        return formService.generateDownloadUrl(id, attachmentType);
    }
}
