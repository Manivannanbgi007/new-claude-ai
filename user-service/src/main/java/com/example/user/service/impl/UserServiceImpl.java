package com.example.user.service.impl;

import com.example.user.dto.PageResponse;
import com.example.user.dto.UserCreateRequest;
import com.example.user.dto.UserResponse;
import com.example.user.dto.UserSearchRequest;
import com.example.user.dto.UserStatusUpdateRequest;
import com.example.user.dto.UserUpdateRequest;
import com.example.user.exception.DuplicateEmailException;
import com.example.user.exception.InvalidUserStateException;
import com.example.user.exception.StaleVersionException;
import com.example.user.exception.UserNotFoundException;
import com.example.user.mapper.UserMapper;
import com.example.user.model.SortDirection;
import com.example.user.model.User;
import com.example.user.model.UserStatus;
import com.example.user.repository.UserRepository;
import com.example.user.repository.UserSpecifications;
import com.example.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final UserMapper mapper;

    public UserServiceImpl(UserRepository userRepository, UserMapper mapper) {
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public UserResponse create(UserCreateRequest request) {
        User user = mapper.toEntity(request);
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateEmailException(user.getEmail());
        }
        User saved = userRepository.saveAndFlush(user);
        log.info("Created user id={}", saved.getId());   // no email/name in logs: personal data
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(String id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(UserSearchRequest request) {
        Sort.Direction direction = request.sortDirection() == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Second sort key "id" keeps paging stable when many users share a sort value.
        Sort sort = Sort.by(direction, request.sortBy().getProperty()).and(Sort.by("id"));
        PageRequest pageable = PageRequest.of(request.page(), request.size(), sort);

        return PageResponse.from(
                userRepository.findAll(UserSpecifications.matching(request.q(), request.email(), request.status()), pageable),
                mapper::toResponse);
    }

    @Override
    @Transactional
    public UserResponse update(String id, UserUpdateRequest request) {
        User user = findOrThrow(id);
        ensureNotDeleted(user);
        ensureVersion(user, request.version());

        String email = mapper.normalizeEmail(request.email());
        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw new DuplicateEmailException(email);
        }

        user.updateProfile(email, request.firstName().trim(), request.lastName().trim(),
                mapper.trimToNull(request.phoneNumber()));
        // saveAndFlush: "UPDATE ... WHERE id=? AND version=?" runs now, so a concurrent
        // update of the same version fails here with 409.
        User saved = userRepository.saveAndFlush(user);
        log.info("Updated user id={} version={}", saved.getId(), saved.getVersion());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse changeStatus(String id, UserStatusUpdateRequest request) {
        if (request.status() == UserStatus.DELETED) {
            throw new InvalidUserStateException("Use DELETE /api/v1/users/{id} to delete a user");
        }
        User user = findOrThrow(id);
        ensureVersion(user, request.version());

        if (user.getStatus() == request.status()) {
            return mapper.toResponse(user);   // already in that status: nothing to do (idempotent)
        }
        if (!user.changeStatus(request.status())) {
            throw new InvalidUserStateException("A " + user.getStatus() + " user cannot become " + request.status());
        }
        User saved = userRepository.saveAndFlush(user);
        log.info("Changed status of user id={} to {}", saved.getId(), saved.getStatus());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(String id) {
        User user = findOrThrow(id);
        if (user.isDeleted()) {
            return;   // deleting twice is harmless (idempotent)
        }
        user.changeStatus(UserStatus.DELETED);
        userRepository.saveAndFlush(user);
        log.info("Deleted (soft) user id={}", id);
    }

    private User findOrThrow(String id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private static void ensureNotDeleted(User user) {
        if (user.isDeleted()) {
            throw new InvalidUserStateException("User '" + user.getId() + "' is deleted and cannot be changed");
        }
    }

    private static void ensureVersion(User user, long expected) {
        if (user.getVersion() != expected) {
            throw new StaleVersionException(user.getId(), expected, user.getVersion());
        }
    }
}
