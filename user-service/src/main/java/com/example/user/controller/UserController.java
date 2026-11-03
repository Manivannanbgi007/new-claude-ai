package com.example.user.controller;

import com.example.user.dto.PageResponse;
import com.example.user.dto.UserCreateRequest;
import com.example.user.dto.UserResponse;
import com.example.user.dto.UserSearchRequest;
import com.example.user.dto.UserStatusUpdateRequest;
import com.example.user.dto.UserUpdateRequest;
import com.example.user.service.UserService;
import com.example.user.util.AppConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/** User management REST API. HTTP concerns only; all rules live in UserService. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 201 Created + Location header. */
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        UserResponse created = userService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Search, filter, sort and page users. All parameters optional. */
    @GetMapping
    public PageResponse<UserResponse> search(@Valid @ModelAttribute UserSearchRequest request) {
        return userService.search(request);
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable @Pattern(regexp = AppConstants.ID_REGEX) String id) {
        return userService.getById(id);
    }

    /** Full profile update; requires the current version. */
    @PutMapping("/{id}")
    public UserResponse update(@PathVariable @Pattern(regexp = AppConstants.ID_REGEX) String id,
                               @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(id, request);
    }

    /** Suspend or re-activate. */
    @PutMapping("/{id}/status")
    public UserResponse changeStatus(@PathVariable @Pattern(regexp = AppConstants.ID_REGEX) String id,
                                     @Valid @RequestBody UserStatusUpdateRequest request) {
        return userService.changeStatus(id, request);
    }

    /** Soft delete: 204, also when the user was already deleted. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Pattern(regexp = AppConstants.ID_REGEX) String id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
