package com.nigson.experience.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeadRequestSourceTest {
    @Test
    void ignoresForwardedHeadersFromUntrustedPeers() {
        var request = request("198.51.100.4", "203.0.113.7");

        assertThat(new LeadRequestSource("10.0.0.0/8").resolve(request)).isEqualTo("198.51.100.4");
        verify(request, never()).getHeaders("X-Forwarded-For");
    }

    @Test
    void resolvesTheFirstUntrustedAddressFromAForwardedProxyChain() {
        var request = request("10.0.0.10", "203.0.113.99, 198.51.100.5, 10.0.0.9");

        assertThat(new LeadRequestSource("10.0.0.0/8").resolve(request)).isEqualTo("198.51.100.5");
    }

    @Test
    void rejectsMalformedForwardedAddressesFromTrustedPeers() {
        var request = request("10.0.0.10", "not-an-address");

        assertThatThrownBy(() -> new LeadRequestSource("10.0.0.0/8").resolve(request))
                .hasMessage("VALIDATION_ERROR");
    }

    @Test
    void rejectsInvalidTrustedProxyRanges() {
        assertThatThrownBy(() -> new LeadRequestSource("10.0.0.0/99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LEAD_TRUSTED_PROXY_CIDR_INVALID");
    }

    private static HttpServletRequest request(String peer, String forwarded) {
        var request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(peer);
        when(request.getHeaders("X-Forwarded-For"))
                .thenReturn(Collections.enumeration(List.of(forwarded)));
        return request;
    }
}
