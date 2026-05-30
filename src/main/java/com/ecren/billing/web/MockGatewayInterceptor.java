package com.ecren.billing.web;

import com.ecren.billing.gateway.MockPaymentGateway;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class MockGatewayInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("X-Mock-Gateway-Result");
        if ("FAIL".equalsIgnoreCase(header)) {
            MockPaymentGateway.SHOULD_FAIL.set(true);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MockPaymentGateway.SHOULD_FAIL.remove();
    }
}
