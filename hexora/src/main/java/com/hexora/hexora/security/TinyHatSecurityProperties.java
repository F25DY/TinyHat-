package com.hexora.hexora.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tinyhat.security")
public class TinyHatSecurityProperties {

    @Valid
    private Session session = new Session();

    @Valid
    private Reauth reauth = new Reauth();

    @Valid
    private Password password = new Password();

    @Valid
    private Login login = new Login();

    @Valid
    private Mfa mfa = new Mfa();

    @Valid
    private RateLimit ratelimit = new RateLimit();

    private List<String> trustedProxies = new ArrayList<>();

    @Valid
    private WebSocket ws = new WebSocket();

    @Valid
    private Network network = new Network();

    @Valid
    private Registration registration = new Registration();

    @Valid
    private OAuth oauth = new OAuth();

    @Valid
    private Csp csp = new Csp();

    @Valid
    private Audit audit = new Audit();

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Reauth getReauth() {
        return reauth;
    }

    public void setReauth(Reauth reauth) {
        this.reauth = reauth;
    }

    public Password getPassword() {
        return password;
    }

    public void setPassword(Password password) {
        this.password = password;
    }

    public Login getLogin() {
        return login;
    }

    public void setLogin(Login login) {
        this.login = login;
    }

    public Mfa getMfa() {
        return mfa;
    }

    public void setMfa(Mfa mfa) {
        this.mfa = mfa;
    }

    public RateLimit getRatelimit() {
        return ratelimit;
    }

    public void setRatelimit(RateLimit ratelimit) {
        this.ratelimit = ratelimit;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    public WebSocket getWs() {
        return ws;
    }

    public void setWs(WebSocket ws) {
        this.ws = ws;
    }

    public Network getNetwork() {
        return network;
    }

    public void setNetwork(Network network) {
        this.network = network;
    }

    public Registration getRegistration() {
        return registration;
    }

    public void setRegistration(Registration registration) {
        this.registration = registration;
    }

    public OAuth getOauth() {
        return oauth;
    }

    public void setOauth(OAuth oauth) {
        this.oauth = oauth;
    }

    public Csp getCsp() {
        return csp;
    }

    public void setCsp(Csp csp) {
        this.csp = csp;
    }

    public Audit getAudit() {
        return audit;
    }

    public void setAudit(Audit audit) {
        this.audit = audit;
    }

    public static class Session {
        @Min(1)
        private int idleMinutes = 5;

        @Min(1)
        private int absoluteMinutes = 120;

        @Min(1)
        private int maxPerUser = 1;

        @NotEmpty
        private List<String> pollPaths = new ArrayList<>(
            List.of("/api/network/events/**", "/api/poll/**", "/ws/**"));

        public int getIdleMinutes() {
            return idleMinutes;
        }

        public void setIdleMinutes(int idleMinutes) {
            this.idleMinutes = idleMinutes;
        }

        public int getAbsoluteMinutes() {
            return absoluteMinutes;
        }

        public void setAbsoluteMinutes(int absoluteMinutes) {
            this.absoluteMinutes = absoluteMinutes;
        }

        public int getMaxPerUser() {
            return maxPerUser;
        }

        public void setMaxPerUser(int maxPerUser) {
            this.maxPerUser = maxPerUser;
        }

        public List<String> getPollPaths() {
            return pollPaths;
        }

        public void setPollPaths(List<String> pollPaths) {
            this.pollPaths = pollPaths;
        }
    }

    public static class Reauth {
        @Min(1)
        private int maxAgeMinutes = 5;

        public int getMaxAgeMinutes() {
            return maxAgeMinutes;
        }

        public void setMaxAgeMinutes(int maxAgeMinutes) {
            this.maxAgeMinutes = maxAgeMinutes;
        }
    }

    public static class Password {
        @Min(12)
        private int minLength = 12;

        @Min(1)
        @Max(72)
        private int maxBytes = 72;

        public int getMinLength() {
            return minLength;
        }

        public void setMinLength(int minLength) {
            this.minLength = minLength;
        }

        public int getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(int maxBytes) {
            this.maxBytes = maxBytes;
        }
    }

    public static class Login {
        @Valid
        private Throttle throttle = new Throttle();

        public Throttle getThrottle() {
            return throttle;
        }

        public void setThrottle(Throttle throttle) {
            this.throttle = throttle;
        }

        public static class Throttle {
            @NotEmpty
            private List<@Min(0) Integer> scheduleSeconds = new ArrayList<>(
                List.of(0, 0, 0, 0, 60, 300, 900, 1800));

            @Min(1)
            private int resetWindowMinutes = 30;

            public List<Integer> getScheduleSeconds() {
                return scheduleSeconds;
            }

            public void setScheduleSeconds(List<Integer> scheduleSeconds) {
                this.scheduleSeconds = scheduleSeconds;
            }

            public int getResetWindowMinutes() {
                return resetWindowMinutes;
            }

            public void setResetWindowMinutes(int resetWindowMinutes) {
                this.resetWindowMinutes = resetWindowMinutes;
            }
        }
    }

    public static class Mfa {
        private boolean required = true;

        @Min(1)
        private int maxAttempts = 5;

        private String key = "";

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    public static class RateLimit {
        @Valid
        private Bucket auth = new Bucket(5, 5, 60);
        @Valid
        private Bucket registro = new Bucket(5, 5, 3600);
        @Valid
        private Bucket mfa = new Bucket(5, 5, 60);
        @Valid
        private Bucket token = new Bucket(5, 5, 60);
        @Valid
        private Bucket cveSearch = new Bucket(10, 10, 60);
        @Valid
        private Bucket networkDiscover = new Bucket(1, 1, 60);
        @Valid
        private Bucket pdf = new Bucket(5, 5, 60);
        @Valid
        private Bucket apiGeneral = new Bucket(120, 120, 60);
        @Valid
        private Bucket poll = new Bucket(30, 30, 60);

        public Bucket getAuth() {
            return auth;
        }

        public void setAuth(Bucket auth) {
            this.auth = auth;
        }

        public Bucket getRegistro() {
            return registro;
        }

        public void setRegistro(Bucket registro) {
            this.registro = registro;
        }

        public Bucket getMfa() {
            return mfa;
        }

        public void setMfa(Bucket mfa) {
            this.mfa = mfa;
        }

        public Bucket getToken() {
            return token;
        }

        public void setToken(Bucket token) {
            this.token = token;
        }

        public Bucket getCveSearch() {
            return cveSearch;
        }

        public void setCveSearch(Bucket cveSearch) {
            this.cveSearch = cveSearch;
        }

        public Bucket getNetworkDiscover() {
            return networkDiscover;
        }

        public void setNetworkDiscover(Bucket networkDiscover) {
            this.networkDiscover = networkDiscover;
        }

        public Bucket getPdf() {
            return pdf;
        }

        public void setPdf(Bucket pdf) {
            this.pdf = pdf;
        }

        public Bucket getApiGeneral() {
            return apiGeneral;
        }

        public void setApiGeneral(Bucket apiGeneral) {
            this.apiGeneral = apiGeneral;
        }

        public Bucket getPoll() {
            return poll;
        }

        public void setPoll(Bucket poll) {
            this.poll = poll;
        }
    }

    public static class Bucket {
        @Min(1)
        private int capacity;

        @Min(1)
        private int refillTokens;

        @Min(1)
        private int refillSeconds;

        public Bucket() {
        }

        public Bucket(int capacity, int refillTokens, int refillSeconds) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillSeconds = refillSeconds;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(int refillTokens) {
            this.refillTokens = refillTokens;
        }

        public int getRefillSeconds() {
            return refillSeconds;
        }

        public void setRefillSeconds(int refillSeconds) {
            this.refillSeconds = refillSeconds;
        }
    }

    public static class WebSocket {
        @NotEmpty
        private List<String> allowedOrigins = new ArrayList<>(List.of("https://localhost:8443"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Network {
        @Min(1)
        @Max(32)
        private int maxPrefix = 24;

        private boolean allowPublicRanges;

        @Min(1)
        private int maxThreads = 64;

        @Min(0)
        private int scanCooldownSeconds = 60;

        public int getMaxPrefix() {
            return maxPrefix;
        }

        public void setMaxPrefix(int maxPrefix) {
            this.maxPrefix = maxPrefix;
        }

        public boolean isAllowPublicRanges() {
            return allowPublicRanges;
        }

        public void setAllowPublicRanges(boolean allowPublicRanges) {
            this.allowPublicRanges = allowPublicRanges;
        }

        public int getMaxThreads() {
            return maxThreads;
        }

        public void setMaxThreads(int maxThreads) {
            this.maxThreads = maxThreads;
        }

        public int getScanCooldownSeconds() {
            return scanCooldownSeconds;
        }

        public void setScanCooldownSeconds(int scanCooldownSeconds) {
            this.scanCooldownSeconds = scanCooldownSeconds;
        }
    }

    public static class Registration {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class OAuth {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Csp {
        private boolean reportOnly = true;

        public boolean isReportOnly() {
            return reportOnly;
        }

        public void setReportOnly(boolean reportOnly) {
            this.reportOnly = reportOnly;
        }
    }

    public static class Audit {
        @Min(1)
        private int enumerationThreshold = 20;

        public int getEnumerationThreshold() {
            return enumerationThreshold;
        }

        public void setEnumerationThreshold(int enumerationThreshold) {
            this.enumerationThreshold = enumerationThreshold;
        }
    }
}
