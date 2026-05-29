package org.spacehub.utils;

import org.spacehub.entities.User.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    public static String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User userDetails) {
            return userDetails.getEmail(); 
        }
        return null;
    }
}
