package com.example.user.service;

import com.example.user.dto.PageResponse;
import com.example.user.dto.UserCreateRequest;
import com.example.user.dto.UserResponse;
import com.example.user.dto.UserSearchRequest;
import com.example.user.dto.UserStatusUpdateRequest;
import com.example.user.dto.UserUpdateRequest;

/** User management use cases. */
public interface UserService {

    UserResponse create(UserCreateRequest request);

    UserResponse getById(String id);

    PageResponse<UserResponse> search(UserSearchRequest request);

    UserResponse update(String id, UserUpdateRequest request);

    UserResponse changeStatus(String id, UserStatusUpdateRequest request);

    /** Soft delete: the user row is kept (status DELETED) because orders refer to it. */
    void delete(String id);
}
