(function () {
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const nativeFetch = window.fetch.bind(window);
    const safeMethods = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);

    window.fetch = async function (input, init) {
        const options = init ? { ...init } : {};
        const requestMethod = input instanceof Request ? input.method : "GET";
        const method = (options.method || requestMethod).toUpperCase();
        const headers = new Headers(options.headers || (input instanceof Request ? input.headers : undefined));

        if (csrfToken && csrfHeader && !safeMethods.has(method)) {
            headers.set(csrfHeader, csrfToken);
        }
        options.headers = headers;
        if (!options.credentials) {
            options.credentials = "same-origin";
        }

        const response = await nativeFetch(input, options);
        if (response.status === 401 && window.location.pathname !== "/login") {
            let loginReason = "expired";
            try {
                if ((await response.clone().json()).error === "SESSION_REPLACED") {
                    loginReason = "replaced";
                }
            } catch (_) {
                // Non-JSON 401 responses still represent an expired authentication.
            }
            window.location.assign(`/login?${loginReason}`);
        } else if (response.status === 403) {
            document.dispatchEvent(new CustomEvent("hexora:forbidden"));
        }
        return response;
    };
})();
