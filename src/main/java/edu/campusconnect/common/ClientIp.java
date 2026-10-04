package edu.campusconnect.common;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {

    private ClientIp() {}

    /**
     * Remote address as resolved by the servlet container. Behind a reverse proxy set
     * {@code server.forward-headers-strategy=native} so this reflects the real client.
     */
    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
