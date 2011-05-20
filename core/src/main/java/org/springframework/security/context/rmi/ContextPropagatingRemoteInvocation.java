/* Copyright 2004, 2005, 2006 Acegi Technology Pty Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.security.context.rmi;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.remoting.support.RemoteInvocation;
import org.springframework.security.Authentication;
import org.springframework.security.context.SecurityContextHolder;
import org.springframework.security.providers.UsernamePasswordAuthenticationToken;

import java.lang.reflect.InvocationTargetException;


/**
 * The actual {@code RemoteInvocation} that is passed from the client to the server.
 * <p>
 * The principal and credentials information will be extracted from the current
 * security context and passed to the server as part of the invocation object.
 * <p>
 * To avoid potential serialization-based attacks, this implementation interprets the values as {@code String}s
 * and creates a {@code UsernamePasswordAuthenticationToken} on the server side to hold them. If a different
 * token type is required you can override the {@code createAuthenticationRequest} method.
 *
 * @author James Monaghan
 * @author Ben Alex
 * @author Luke Taylor
 */
public class ContextPropagatingRemoteInvocation extends RemoteInvocation {
    //~ Static fields/initializers =====================================================================================

    private static final Log logger = LogFactory.getLog(ContextPropagatingRemoteInvocation.class);

    //~ Instance fields ================================================================================================

    private final String principal;
    private final String credentials;

    //~ Constructors ===================================================================================================

    /**
     * Constructs the object, storing the principal and credentials extracted from the client-side
     * security context.
     *
     * @param methodInvocation the method to invoke
     */
    public ContextPropagatingRemoteInvocation(MethodInvocation methodInvocation) {
        super(methodInvocation);
        Authentication currentUser = SecurityContextHolder.getContext().getAuthentication();

        if (currentUser != null) {
            principal = currentUser.getPrincipal().toString();
            credentials = currentUser.getCredentials().toString();
        } else {
            principal = credentials = null;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("RemoteInvocation now has principal: " + principal);
        }
    }

    //~ Methods ========================================================================================================

    /**
     * Invoked on the server-side.
     * <p>
     * The transmitted principal and credentials will be used to create an unauthenticated {@code Authentication}
     * instance for processing by the {@code AuthenticationManager}.
     *
     * @param targetObject the target object to apply the invocation to
     *
     * @return the invocation result
     *
     * @throws NoSuchMethodException if the method name could not be resolved
     * @throws IllegalAccessException if the method could not be accessed
     * @throws InvocationTargetException if the method invocation resulted in an exception
     */
    public Object invoke(Object targetObject)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {

        if (principal != null) {
            Authentication request = createAuthenticationRequest(principal, credentials);
            request.setAuthenticated(false);
            SecurityContextHolder.getContext().setAuthentication(request);

            if (logger.isDebugEnabled()) {
                logger.debug("Set SecurityContextHolder to contain: " + request);
            }
        }

        try {
            return super.invoke(targetObject);
        } finally {
            SecurityContextHolder.clearContext();

            if (logger.isDebugEnabled()) {
                logger.debug("Cleared SecurityContextHolder.");
            }
        }
    }

    /**
     * Creates the server-side authentication request object.
     */
    protected Authentication createAuthenticationRequest(String principal, String credentials) {
        return new UsernamePasswordAuthenticationToken(principal, credentials);
    }
}
