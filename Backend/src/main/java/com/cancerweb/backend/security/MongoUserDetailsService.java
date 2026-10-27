package com.cancerweb.backend.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.service.UserService;

/**
 * Looks accounts up by email for the password check at login. The returned username is the account id, so the
 * login code can load the user without a second email lookup.
 */
@Service
public class MongoUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public MongoUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmail(UserService.normalizeEmail(email))
                .map(user -> org.springframework.security.core.userdetails.User.withUsername(user.id())
                        .password(user.passwordHash())
                        .authorities(user.role().authority())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No account for this email"));
    }
}
