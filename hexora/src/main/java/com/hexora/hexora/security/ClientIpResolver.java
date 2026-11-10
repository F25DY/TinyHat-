package com.hexora.hexora.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    private final TinyHatSecurityProperties properties;

    public ClientIpResolver(TinyHatSecurityProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || !isTrustedProxy(remoteAddress)) {
            return remoteAddress == null ? "unknown" : remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }

        List<String> chain = new ArrayList<>();
        for (String value : forwardedFor.split(",")) {
            String address = value.trim();
            if (!isIpAddress(address)) {
                return remoteAddress;
            }
            chain.add(address);
        }
        chain.add(remoteAddress);

        for (int index = chain.size() - 1; index >= 0; index--) {
            String candidate = chain.get(index);
            if (!isTrustedProxy(candidate)) {
                return candidate;
            }
        }
        return remoteAddress;
    }

    private boolean isTrustedProxy(String address) {
        for (String trustedProxy : properties.getTrustedProxies()) {
            if (matches(address, trustedProxy)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String address, String network) {
        String[] parts = network.trim().split("/", -1);
        if (parts.length > 2 || !isIpAddress(parts[0]) || !isIpAddress(address)) {
            return false;
        }
        try {
            byte[] candidate = InetAddress.getByName(address).getAddress();
            byte[] networkAddress = InetAddress.getByName(parts[0]).getAddress();
            if (candidate.length != networkAddress.length) {
                return false;
            }
            int prefix = parts.length == 1 ? networkAddress.length * 8 : Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > networkAddress.length * 8) {
                return false;
            }
            int wholeBytes = prefix / 8;
            int remainingBits = prefix % 8;
            for (int index = 0; index < wholeBytes; index++) {
                if (candidate[index] != networkAddress[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xff << (8 - remainingBits);
            return (candidate[wholeBytes] & mask) == (networkAddress[wholeBytes] & mask);
        } catch (NumberFormatException | UnknownHostException exception) {
            return false;
        }
    }

    private boolean isIpAddress(String value) {
        if (value == null || value.isBlank() || value.contains("%")) {
            return false;
        }
        if (value.indexOf(':') >= 0) {
            try {
                return InetAddress.getByName(value).getAddress().length == 16;
            } catch (UnknownHostException exception) {
                return false;
            }
        }
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3
                || (octet.length() > 1 && octet.charAt(0) == '0')
                || !octet.chars().allMatch(Character::isDigit)) {
                return false;
            }
            try {
                if (Integer.parseInt(octet) > 255) {
                    return false;
                }
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }
}
