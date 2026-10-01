package com.example.cleancarsapi.config;

import com.example.cleancarsapi.exception.ServiceUnavailableException;
import com.example.cleancarsapi.service.FeaturePropertyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * While {@code Client.Maintenance.Mode.Enable} is true, every tenant {@code /api/**}
 * call answers 503 {@code maintenance_mode}. Exempt paths are registered in
 * {@link WebConfig}.
 */
@Component
@RequiredArgsConstructor
public class MaintenanceModeInterceptor implements HandlerInterceptor {

    private final FeaturePropertyService featureProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS preflights carry no credentials and no data — let them through.
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        if (featureProperties.isEnabled(FeaturePropertyService.MAINTENANCE_MODE)) {
            throw ServiceUnavailableException.maintenanceMode();
        }
        return true;
    }
}
