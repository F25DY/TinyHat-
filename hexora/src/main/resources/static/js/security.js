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
                const code = (await response.clone().json()).error;
                if (code === "SESSION_REPLACED") {
                    loginReason = "replaced";
                } else if (code === "REAUTH_REQUIRED") {
                    // The session is still valid; the caller (for example the reports panel) asks for re-authentication.
                    return response;
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

    document.addEventListener("submit", event => {
        const form = event.target instanceof HTMLFormElement ? event.target : null;
        const message = form?.dataset.confirmMessage;
        if (form && message && !window.confirm(message)) {
            event.preventDefault();
        }
    });
})();
