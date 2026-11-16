# TinyHat — Trazabilidad de la capa de seguridad v1

Une cada decisión cerrada del plan (§1) con la fase que la implementa, los criterios de aceptación y las pruebas. Los nombres de prueba son métodos reales; se pueden buscar con `grep -rn "<nombre>" hexora/src/test`.

Estados: **PASA** (hay prueba automatizada o paso comprobado), **PARCIAL** (hay prueba, pero no cubre todo lo que pedía el plan), **NO IMPLEMENTADO** (decisión del usuario).

## 1. Decisiones del §1 del plan

| # | Decisión | Fase | Criterios | Pruebas | Estado |
|---|---|---|---|---|---|
| 1 | Sesión de servidor (cookie); **no JWT** | 3, 5 | F3-CA2, F3-CA3, F3-CA5, F5-CA1, F5-CA2, F5-CA8 | `PhaseThreeSecurityIntegrationTest.unauthenticatedWebRedirectsAndApiReturnsJson401`, `.csrfProtectsWritesAndSecurityHeadersArePresent`, `.successfulLoginAuditsAndLogoutRequiresPostAndCsrf`; `PhaseFiveSessionHttpIntegrationTest.sessionCookieIsSecureAndLoginRotatesTheSessionIdWithoutUrlRewriting`. Además `grep -ciE "jjwt\|jwt\|oauth2" hexora/pom.xml` devuelve 0. | PASA |
| 2 | Roles `AUDITOR` y `CLIENTE_ADMIN`; el rol nunca viene de una petición | 3, 4, 8, 14 | F3-CA1, F3-CA6, F4-CA3 (informe), F4-CA5 (plan), F8-CA1, F14-CA3 | `PhaseThreeSecurityIntegrationTest.unlistedRoutesAreDeniedAndEveryControllerRouteHasExplicitMethodSecurity`; `PhaseEightRegistrationSecurityIntegrationTest.f8ca1_registrationIgnoresSubmittedRoleAndAccountId`; `CrossAccountIsolationIntegrationTest.requestBodiesCannotChooseTheAccountTheRoleOrTheRisk` (parámetros `cuentaId`, `rol` y `organizacionId` en proyecto, área y hallazgo se ignoran); `PhaseFourTenantIsolationIntegrationTest.auditorsAndClientAdminsOnlySeeProjectsWithinTheirAssignmentOrOrganization`; `ReportFoundationIntegrationTest.clientAdminReadsOnlyPublishedReportsOfTheOwnOrganizationAndCannotWrite`; `FinalEndpointInventoryIntegrationTest.everyRouteIsDeclaredPublicOrDeniedToAnonymousUsers`. Los DTO de entrada no tienen campos de cuenta, organización ni rol. | PASA |
| 3 | Aislamiento por cuenta y por organización; recurso ajeno ⇒ **404** | 2, 4, 10, 14 | F2-CA3, F4-CA1 a CA8 (informe), F4-CA1, CA2, CA4 y CA9 (plan), F10-CA8 | **`CrossAccountIsolationIntegrationTest.anAuditorNeverReachesForeignOrUnassignedProjectsAreasOrFindings`** (95 peticiones GET/POST sobre las 21 rutas de proyecto, área y hallazgo con IDs de otra cuenta, de otro proyecto de la misma cuenta sin asignación, inventados y mezclados en rutas anidadas: todas 404 y ninguna fila cambia), `.foreignAndMissingResourcesAnswerWithTheSameBody`; `PhaseTwoMigrationIntegrationTest.rejectsNetworkScanReferencingHostFromAnotherAccount` (la BD lo impide); `PhaseFourTenantIsolationIntegrationTest.hibernateFilterScopesKeyLookupsAndWritesUseOnlyTheActiveTenant`, `.clearingNetworkHistoryDeletesOnlyTheActiveTenantHistory`; `ReportFoundationIntegrationTest.auditorsOfOtherAccountsAndUnassignedAuditorsOfTheSameAccountGetANotFound`, `.theSnapshotNeverMixesDataFromOtherProjectsOrAccounts`; `NetworkMonitoringWebTest.rejectsHistoryAndHostMutationsForAHostOwnedByAnotherProject`, `.doesNotShareDiscoveryDataBetweenProjectsWithTheSameAuthorizedRange`, `.doesNotExposeRangesForProjectsOutsideTheAuditorsAssignments`; `NetworkMonitorWebSocketHandlerTest.publishesOnlyToSessionsInTheActiveTenant`; `PhaseTwelveAuditIntegrationTest.deniedAccessScopedNotFoundAndEnumerationWarningAreRecorded` | PASA. La matriz se comprobó con una mutación: sin la exigencia de asignación al proyecto, 4 sondeos devolvieron 200/302 y la prueba falló |
| 4 | Sesión única; inactividad 5 min; tope 120 min; los sondeos no cuentan | 5, 14 | F5-CA3 a CA7 | `SessionActivityFilterTest.expiresApiSessionAfterFiveMinutesOfInactivity`, `.continuousPollingStillExpiresAfterTheIdleLimit`, `.pollingDoesNotRefreshActivityButAnInteractiveRequestDoes`, `.absoluteTimeoutExpiresDespiteRecentActivity`, `.suspendedUserIsRevokedOnTheNextRequest`; `PhaseThreeSecurityIntegrationTest.secondLoginReplacesThePreviousSession`; **`SessionPollPathsConsistencyTest`** (lee `network-monitoring.js` y `application.properties`: toda ruta que la pantalla consulta con `setInterval` debe estar en `poll-paths`, y las acciones del usuario no) | PASA. H-1 corregido en la Fase 14 (ver el informe) |
| 5 | TOTP obligatorio para ambos roles; OAuth2/OIDC como primer factor alternativo | 7, 9 | F7-CA1 a CA9; F9-CA1 a CA8 | `MfaFlowIntegrationTest` (3 pruebas), `MfaServiceEncryptionTest`, `MfaKeyValidatorTest`, `TotpServiceTest`, `ProfileSecurityGuardMfaTest` | TOTP: PASA. **OAuth/OIDC: NO IMPLEMENTADO** por decisión del usuario (2026-11-09); F9-CA1 a CA8 sin verificar |
| 6 | El registro propio solo crea AUDITOR; CLIENTE_ADMIN solo por invitación | 8 | F8-CA1, F8-CA5, F8-CA6 | `PhaseEightRegistrationSecurityIntegrationTest.f8ca1_registrationIgnoresSubmittedRoleAndAccountId`, `.f8ca5_unverifiedUserCannotLoginUntilVerified`, `.f8ca6_auditorCannotInviteForAnotherAccountOrganization` | PASA |
| 7 | Limitador de intentos de login y *rate limit* por clase de endpoint | 6 | F6-CA1 a CA8 | `LoginAttemptServiceTest`, `LoginAttemptFilterTest`, `IpRateLimitFilterTest`, `UserRateLimitFilterTest`, `RateLimitServiceTest`, `RateLimitCategoryTest`, `ClientIpResolverTest`, `CveServiceTest.capsNvdCallsAcrossSearchesAtConfiguredGlobalBudget` | PASA (F6-CA5 queda cubierto por `MfaFlowIntegrationTest.recoveryCodesCanOnlyBeUsedOnceAndFiveInvalidTotpAttemptsEndTheSession`) |
| 8 | Banderas de capacidad por cuenta (todas `true`), consultadas desde un único servicio | 2, 10, 12 | F2-CA5, F10-CA10 | `PhaseTwoMigrationIntegrationTest.enablesAllCapabilitiesForInitialAccount`; `NetworkMonitoringWebTest.rejectsNetworkApiCallsWhenTheNetworkCapabilityIsDisabled`; `NetworkCapabilityGuardTest.deniesMissingOrDisabledModuleForApiAndBackgroundScans`; `ReportFoundationIntegrationTest.aDisabledCapabilityBlocksCreationAndExport`; `NetworkMonitoringSchedulerTest.doesNotMonitorWhenTheAccountContinuousMonitoringCapabilityIsDisabled` | PASA |
| 9 | El escaneo exige rango autorizado por proyecto y verificación de la interfaz activa, también en demo | 10 | F10-CA1 a CA5, CA11 | `NetworkScopeGuardTest` (7 pruebas), `NetworkMonitoringServiceTest.rejectsDiscoveryBeforeReadingTheNetworkWhenProjectInterfaceIsOutOfScope`, `.rejectsRangesLargerThanTheConfiguredLimitBeforeLoadingHost`, `NetworkMonitoringWebTest.rejectsAProjectScanWithoutAnAuthorizedRangeAndAuditsTheRejectedTarget`, `.rejectsAnOversizedRangeBeforeStartingAScan`, `Ipv4CidrTest` | PASA |
| 10 | Demo en red de laboratorio, enlazada a `127.0.0.1`, con datos ficticios | 1, 10, 14 | F1-CA3, F10-CA11, F14-CA4 | `FinalEndpointInventoryIntegrationTest.defaultConfigurationBindsToLoopbackAndKeepsDemoAndRangeSafeguardsOff` (dirección, sin sembrado, sin rangos públicos, prefijo máximo 24, MFA obligatorio); `ProfileSecurityGuard*Test` (la guarda de `prod`) | PASA en configuración. "Red de laboratorio" y "datos ficticios" son reglas de operación: las cubre el runbook (`runbook-demo.md`), que la persona debe recorrer (F14-CA4) |

## 2. Resumen por fase

Los identificadores y resultados salen de los informes `reports/fase-NN.md`. Total: 99 criterios de F0 a F13.

| Fase | Tema | Criterios | PASA | Otro estado |
|---|---|---|---|---|
| 0 | Inventario y línea base | 3 | 3 | F0-CA3 se registró con 14 errores por MySQL inaccesible en ese momento; la suite actual está verde |
| 1 | Configuración segura | 5 | 5 | |
| 2 | Modelo de datos (V18–V23) | 5 | 5 | |
| 3 | Núcleo de Spring Security | 8 | 8 | |
| 4 | Aislamiento | 8 (informe) | 8 | El informe usa criterios distintos de los 9 del plan; la Fase 14 añadió la matriz del plan (`CrossAccountIsolationIntegrationTest`) |
| 5 | Sesión | 8 | 8 | H-1 corregido en la Fase 14 |
| 6 | Intentos y *rate limit* | 8 | 7 | F6-CA5 cubierto en F7 |
| 7 | MFA (TOTP) | 9 | 9 | |
| 8 | Registro e invitaciones | 10 | 10 | |
| 9 | OAuth2 / OIDC | 8 | 0 | **8 no implementados** (decisión del usuario) |
| 10 | Módulo de red y WebSocket | 11 | 11 | |
| 11 | Entradas, salidas, cabeceras, PDF | 8 | 6 | **F11-CA4 y F11-CA8 parciales** (no hay PDF); F11-CA3 pide además confirmación visual de la persona |
| 12 | Auditoría y registros | 4 | 4 | |
| 13 | Pipeline CI/CD | 4 | 3 | **F13-CA2 pendiente de la persona** (PR de prueba en GitHub) |
| **Total** | | **99** | **87** | 1 cubierto en otra fase, 2 parciales, 1 pendiente de la persona, 8 no implementados |

## 3. Migraciones frente al plan

El plan reservaba V18 a V27. La numeración real se desplazó; las fases lo registraron en `IMPLEMENTATION_LOG.md`.

| Plan | Real |
|---|---|
| V18 a V23 | V18 a V23 (cuenta, usuario, UUID, capacidades, informe, auditoría) |
| V24 `mfa_credencial` | **V24 normalización de puertos monitoreados**; MFA pasó a **V25** |
| V25 tokens | **V26** `tokens de seguridad` |
| V26 `identidad_externa` | No existe (OAuth no se implementó) |
| V27 `rango_autorizado` | V27 |
| — | **V28** `report_content_foundation` (cimientos de informes, Fase 12) |
| — | **V29** `activate_pending_registered_users` (posterior a la Fase 14, por instrucción de la persona: activa dos usuarios de prueba locales; no hace nada en una base nueva) |

## 4. Fuentes de cada fila

Informes: `reports/fase-00.md` a `fase-13.md` (los de las fases 0 a 2 están en `hexora/docs/security/reports/`; los demás, en `docs/security/reports/`). Desviaciones: `IMPLEMENTATION_LOG.md`.
