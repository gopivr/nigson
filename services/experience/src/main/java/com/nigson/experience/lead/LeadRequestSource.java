package com.nigson.experience.lead;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import com.nigson.shared.problem.ApiValues;

final class LeadRequestSource {
    private record Network(byte[] address, int prefixLength) {
        boolean contains(InetAddress candidate) {
            byte[] candidateBytes = candidate.getAddress();
            if (address.length != candidateBytes.length) return false;
            int wholeBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < wholeBytes; i++) {
                if (address[i] != candidateBytes[i]) return false;
            }
            if (remainingBits == 0) return true;
            int mask = 0xff << (8 - remainingBits);
            return (address[wholeBytes] & mask) == (candidateBytes[wholeBytes] & mask);
        }
    }

    private final List<Network> trustedProxies;

    LeadRequestSource(String cidrList) {
        if (cidrList == null || cidrList.isBlank()) {
            trustedProxies = List.of();
            return;
        }
        trustedProxies = java.util.Arrays.stream(cidrList.split(","))
                .map(String::strip).filter(value -> !value.isEmpty()).map(LeadRequestSource::network).toList();
    }

    static LeadRequestSource from(Environment env) {
        return new LeadRequestSource(env.getProperty("NIGSON_LEADS_TRUSTED_PROXY_CIDRS", ""));
    }

    String resolve(HttpServletRequest request) {
        InetAddress peer = address(request.getRemoteAddr());
        if (!trusted(peer)) return text(peer);

        List<String> headers = Collections.list(request.getHeaders("X-Forwarded-For"));
        if (headers.isEmpty()) return text(peer);
        if (headers.size() != 1 || headers.getFirst().length() > 1024) throw ApiValues.invalid();
        String[] parts = headers.getFirst().split(",", -1);
        if (parts.length > 10) throw ApiValues.invalid();
        List<InetAddress> chain = new ArrayList<>(parts.length);
        for (String part : parts) chain.add(address(part.strip()));
        for (int i = chain.size() - 1; i >= 0; i--) {
            InetAddress candidate = chain.get(i);
            if (!trusted(candidate)) return text(candidate);
        }
        throw ApiValues.invalid();
    }

    private boolean trusted(InetAddress address) {
        return trustedProxies.stream().anyMatch(network -> network.contains(address));
    }

    private static Network network(String value) {
        String[] parts = value.split("/", -1);
        if (parts.length != 2) throw new IllegalArgumentException("LEAD_TRUSTED_PROXY_CIDR_INVALID");
        InetAddress address;
        int prefix;
        try {
            address = address(parts[0]);
            prefix = Integer.parseInt(parts[1]);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("LEAD_TRUSTED_PROXY_CIDR_INVALID");
        }
        int bits = address.getAddress().length * 8;
        if (prefix < 0 || prefix > bits) throw new IllegalArgumentException("LEAD_TRUSTED_PROXY_CIDR_INVALID");
        return new Network(address.getAddress(), prefix);
    }

    private static InetAddress address(String value) {
        if (value == null || value.isBlank() || !value.matches("[0-9a-fA-F:.]+")) throw ApiValues.invalid();
        try {
            if (!value.contains(":")) {
                String[] octets = value.split("\\.", -1);
                if (octets.length != 4) throw ApiValues.invalid();
                byte[] bytes = new byte[4];
                for (int i = 0; i < octets.length; i++) {
                    int part = Integer.parseInt(octets[i]);
                    if (part < 0 || part > 255) throw ApiValues.invalid();
                    bytes[i] = (byte) part;
                }
                return InetAddress.getByAddress(bytes);
            }
            return InetAddress.getByName(value);
        } catch (UnknownHostException | NumberFormatException invalid) {
            throw ApiValues.invalid();
        }
    }

    private static String text(InetAddress address) {
        return address.getHostAddress().replaceAll("%.*$", "");
    }
}
