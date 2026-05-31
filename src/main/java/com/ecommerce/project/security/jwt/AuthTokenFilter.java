package com.ecommerce.project.security.jwt;

import com.ecommerce.project.security.services.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuthTokenFilter extends OncePerRequestFilter {
    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        logger.info("AuthTokenFilter: dispatcherType={}, method={}, uri={}",
                request.getDispatcherType(), request.getMethod(), request.getRequestURI());
        try {
            String jwt = parseJwt(request);
            logger.info("AuthTokenFilter: jwt present={}", jwt != null);
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String username = jwtUtils.getUserNameFromJwtToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails,
                                null,
                                userDetails.getAuthorities());
                logger.info("AuthTokenFilter: authenticated user={} roles={}",
                        username, userDetails.getAuthorities());

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                logger.info("AuthTokenFilter: no valid jwt -> request stays unauthenticated for uri={}",
                        request.getRequestURI());
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Also authenticate on ASYNC dispatches. Streaming endpoints (e.g. the assistant's
     * {@code Flux} response) are processed via a second, asynchronous dispatch. Since the app is
     * stateless (the security context is not stored in a session), this filter must re-read the
     * JWT on that dispatch too; otherwise the re-dispatched request is unauthenticated and gets a
     * 401 even though a valid token cookie/header is present.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /**
     * Also authenticate on ERROR dispatches (forwards to /error). Without this, any error during a
     * request is re-dispatched unauthenticated and the /error endpoint returns a misleading 401
     * that hides the real cause.
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

//    private String parseJwt(HttpServletRequest request) {
//        String jwt = jwtUtils.getJwtFromCookies(request);
//        logger.debug("AuthTokenFilter.java: {}", jwt);
//        return jwt;
//    }

    private String parseJwt(HttpServletRequest request) {
        String jwtFromCookie = jwtUtils.getJwtFromCookies(request);
        if (jwtFromCookie != null) {
            return jwtFromCookie;
        }

        String jwtFromHeader = jwtUtils.getJwtFromHeader(request);
        if (jwtFromHeader != null) {
            return jwtFromHeader;
        }

        return null;
    }
}

