package com.cancerweb.backend.controller;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.dto.AdminAccountResponse;
import com.cancerweb.backend.dto.ProfileResponse;
import com.cancerweb.backend.exception.OwnerOnlyException;
import com.cancerweb.backend.model.PasswordResetRequest;
import com.cancerweb.backend.model.ResetRequestStatus;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.model.VerificationStatus;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.security.SiteOwner;
import com.cancerweb.backend.service.UserService;

/**
 * The site owner's own Admin account page. SecurityConfig already limits /api/admin/** to ADMIN; this also
 * refuses every administrator except the owner (ADMIN_EMAIL). Edits go through /api/profile like any account.
 */
@RestController
@RequestMapping("/api/admin/account")
public class AdminAccountController {

    private final UserService userService;
    private final SiteOwner owner;
    private final MongoTemplate mongo;

    public AdminAccountController(UserService userService, SiteOwner owner, MongoTemplate mongo) {
        this.userService = userService;
        this.owner = owner;
        this.mongo = mongo;
    }

    @GetMapping
    public AdminAccountResponse account(@AuthenticationPrincipal CurrentUser me) {
        User user = userService.get(me.id());
        if (!owner.is(user)) {
            throw new OwnerOnlyException("Only the site owner can open this page.");
        }
        String email = user.email();
        var activity = new AdminAccountResponse.Activity(
                count(Criteria.where("verification.reviewedBy").is(email).and("verification.status").is(VerificationStatus.VERIFIED), User.class),
                count(Criteria.where("verification.reviewedBy").is(email).and("verification.status").is(VerificationStatus.REJECTED), User.class),
                count(Criteria.where("handledBy").is(email).and("status").is(ResetRequestStatus.RESOLVED), PasswordResetRequest.class),
                count(Criteria.where("handledBy").is(email).and("status").is(ResetRequestStatus.DISMISSED), PasswordResetRequest.class));
        var team = new AdminAccountResponse.Team(
                count(Criteria.where("role").is(Role.ADMIN), User.class),
                count(Criteria.where("role").is(Role.DOCTOR), User.class),
                count(Criteria.where("role").is(Role.PATIENT), User.class),
                count(Criteria.where("disabled").is(true), User.class));
        return new AdminAccountResponse(ProfileResponse.from(user), user.lastLoginAt(), user.passwordChangedAt(),
                activity, team);
    }

    private long count(Criteria criteria, Class<?> type) {
        return mongo.count(Query.query(criteria), type);
    }
}
