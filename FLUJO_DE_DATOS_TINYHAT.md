# Diagrama de Flujo de Datos (DFD) del Sistema TinyHat / Hexora

Este documento describe de manera exhaustiva el flujo de información de todo el sistema **TinyHat** (Hexora), incluyendo sus entidades externas, procesos internos, almacenes de datos (Data Stores) y canales de comunicación en tiempo real.

---

## 🧭 ¿Dónde y cómo puedo ver este flujo de datos?

Tienes varias formas de visualizar estos diagramas de flujo de forma gráfica e interactiva:

1. **Directamente en este editor (VS Code / Antigravity IDE)**:
   - Abre este archivo: [FLUJO_DE_DATOS_TINYHAT.md](file:///c:/Users/Fredy/OneDrive/Documentos/OneDrive/Desktop/Ronin/Uni/4to%20Semestre/Hexora/Hexora-/FLUJO_DE_DATOS_TINYHAT.md).
   - Presiona `Ctrl + Shift + V` (o `Ctrl + K, V`) para abrir la **Vista Previa de Markdown** (Markdown Preview).
   - Los bloques de código `mermaid` se renderizarán automáticamente como diagramas visuales con flechas y colores.
2. **En Mermaid Live Editor (Online sin instalar nada)**:
   - Ve a [mermaid.live](https://mermaid.live).
   - Copia cualquiera de los bloques `mermaid` de este documento y pégalos en el editor de la izquierda. Podrás hacer zoom, moverte por el diagrama e incluso exportarlo como **PNG** o **SVG**.
3. **En GitHub**:
   - Al subir este archivo al repositorio ([Hexora- en GitHub](https://github.com/bertelsarasofia-code/Hexora-)), GitHub renderiza de forma nativa los bloques Mermaid en diagramas visuales.

---

## 1. DFD Nivel 0: Diagrama de Contexto General

El diagrama de contexto muestra la interacción de **TinyHat** con los actores y sistemas externos:

```mermaid
graph TD
    classDef actor fill:#1e293b,stroke:#3b82f6,stroke-width:2px,color:#f8fafc;
    classDef system fill:#0f172a,stroke:#10b981,stroke-width:3px,color:#f8fafc;
    classDef external fill:#334155,stroke:#f59e0b,stroke-width:2px,color:#f8fafc;

    User["Auditor / Pentester<br>(Usuario Operador)"]:::actor
    NVD["API NIST NVD 2.0<br>(Vulnerabilidades CVE)"]:::external
    MyMemory["API MyMemory<br>(Servicio de Traducción)"]:::external
    LocalNet["Red Local IPv4 / Dispositivos<br>(ARP, ICMP, TCP, mDNS, SSDP)"]:::external

    TinyHat(("SISTEMA TINYHAT / HEXORA<br>(Plataforma de Auditoría & Monitoreo)")):::system

    %% Flujos Usuario
    User -->|"1. Credenciales, Datos de Empresas, Proyectos y Áreas"| TinyHat
    User -->|"2. Registro de Vulnerabilidades y Parámetros de Riesgo"| TinyHat
    User -->|"3. Búsqueda de CVEs y Escaneo Manual de Red/Puertos"| TinyHat
    TinyHat -->|"4. Vistas Web, Informes, Dashboard y Alertas WebSocket"| User

    %% Flujos NVD & Traductor
    TinyHat -->|"5. Consulta de CVE (cveId / keyword)"| NVD
    NVD -->|"6. Métricas CVSS 4.0/3.1, CWE y Descripción en Inglés"| TinyHat
    TinyHat -->|"7. Texto en inglés para traducir"| MyMemory
    MyMemory -->|"8. Descripción traducida al español"| TinyHat

    %% Flujos Red Local
    TinyHat -->|"9. Probes ICMP (Ping), TCP Connect y Broadcast ARP"| LocalNet
    LocalNet -->|"10. Respuestas Echo, Tabla ARP, Puertos abiertos/cerrados"| TinyHat
```

---

## 2. DFD Nivel 1: Desglose por Módulos y Almacenes de Datos

Este nivel desglosa los 5 procesos principales que conforman la aplicación TinyHat y su interacción con la base de datos MySQL (Flyway Migrations) y las interfaces de red:

```mermaid
flowchart TB
    classDef proc fill:#1e293b,stroke:#60a5fa,stroke-width:2px,color:#ffffff;
    classDef store fill:#047857,stroke:#34d399,stroke-width:2px,color:#ffffff;
    classDef ext fill:#475569,stroke:#fbbf24,stroke-width:2px,color:#ffffff;

    User(["Usuario / Auditor"]):::ext
    NVD_API(["NVD NIST API"]):::ext
    Trans_API(["MyMemory API"]):::ext
    Host_Net(["Dispositivos Red Local"]):::ext

    subgraph Procesos ["Procesos del Sistema TinyHat"]
        P1["1.0 Autenticación y<br>Gestión de Acceso"]:::proc
        P2["2.0 Gestión de Proyectos<br>& Áreas Auditables"]:::proc
        P3["3.0 Motor de Evaluación de<br>Riesgo TinyHat"]:::proc
        P4["4.0 Sincronización y<br>Enriquecimiento CVE"]:::proc
        P5["5.0 Descubrimiento y<br>Monitoreo de Red"]:::proc
    end

    subgraph DataStores ["Almacenes de Datos (MySQL)"]
        D1[("D1: Empresas, Auditorías<br>y Áreas Auditables")]:::store
        D2[("D2: Vulnerabilidades<br>& Mitigaciones")]:::store
        D3[("D3: Caché CVE Local<br>(cve)")]:::store
        D4[("D4: Inventario de Red & Dispositivos<br>(network_host, network_detected_device)")]:::store
        D5[("D5: Historial de Escaneos y Eventos<br>(network_scan, port_scan_result, events)")]:::store
    end

    %% Flujos P1
    User -->|"Credenciales de login"| P1
    P1 -->|"Acceso permitido -> Redirección Dashboard"| User

    %% Flujos P2
    User -->|"Alta/Edición Empresa, Auditoría y Áreas"| P2
    P2 <-->|"CRUD de estructura organizativa"| D1
    P2 -->|"Datos para Dashboard / Vistas de proyectos"| User

    %% Flujos P3
    User -->|"Parámetros C, I, D, Probabilidad y Detalle"| P3
    P3 -->|"Calcula Impacto y Nivel de Riesgo TinyHat"| P3
    P3 <-->|"Lee/Escribe Vulnerabilidades"| D2
    D1 -.->|"Asocia departamento a auditoría"| D2
    P3 -->|"Lista y métricas de vulnerabilidades"| User

    %% Flujos P4
    User -->|"Búsqueda por código CVE (/api/cves/buscar)"| P4
    P4 <-->|"Consulta primero caché local"| D3
    P4 -->|"Si no existe en caché: Request CVE"| NVD_API
    NVD_API -->|"Datos brutos CVSS y descripción"| P4
    P4 -->|"Traducir descripción a español"| Trans_API
    Trans_API -->|"Descripción traducida"| P4
    P4 -->|"Guarda CVE enriquecido en caché"| D3
    P4 -.->|"Autocompleta formulario de vulnerabilidad"| P3

    %% Flujos P5
    User -->|"Dispara escaneo de subred o puertos"| P5
    P5 <-->|"Sondeo ICMP, consulta ARP, sockets TCP"| Host_Net
    P5 <-->|"Consulta/Actualiza dispositivos detectados"| D4
    P5 -->|"Registra scans, snapshots y eventos"| D5
    P5 -->|"Notificaciones en vivo vía WebSocket (/ws/network)"| User
```

---

## 3. DFD Nivel 2: Detalle de Procesos Críticos

### 3.1. Flujo de Datos: Búsqueda CVE y Cálculo de Riesgo TinyHat

Este flujo detalla cómo se alimenta y calcula el riesgo de una vulnerabilidad según la escala propietaria de TinyHat:

```mermaid
sequenceDiagram
    autonumber
    actor Usuario as Auditor / Usuario
    participant BuscadorJS as cve-buscador.js (Frontend)
    participant CveCtrl as CveController
    participant CveSvc as CveService
    participant CveRepo as CveRepository (MySQL)
    participant NVD as NIST NVD API 2.0
    participant Trad as TraductorService (MyMemory)
    participant VulnCtrl as VulnerabilidadController
    participant VulnRepo as VulnerabilidadRepository (MySQL)

    Usuario->>BuscadorJS: Escribe código CVE (ej. CVE-2021-44228)
    BuscadorJS->>CveCtrl: GET /api/cves/buscar?q=CVE-2021-44228
    CveCtrl->>CveSvc: buscar(q)
    CveSvc->>CveRepo: findTop10ByCodigo... (Caché local)
    alt Existe en BD local
        CveRepo-->>CveSvc: Registro existente
    else No existe en BD
        CveSvc->>NVD: GET /cves/2.0?cveId=CVE-2021-44228
        NVD-->>CveSvc: JSON CVSS (4.0/3.1/2.0, métricas, descripción EN)
        CveSvc->>Trad: traducir(descripciónEN)
        Trad-->>CveSvc: Descripción en Español
        CveSvc->>CveRepo: save(cveEnriquecido)
    end
    CveSvc-->>CveCtrl: Lista de CVEs
    CveCtrl-->>BuscadorJS: 200 OK [Cve DTO]
    BuscadorJS-->>Usuario: Muestra sugerencia y autocompleta campos (Confidencialidad, Integridad, Disponibilidad, Descripción)

    Usuario->>VulnCtrl: POST /proyectos/{pid}/departamentos/{did}/vulnerabilidades/guardar
    Note over VulnCtrl: Motor de Riesgo TinyHat:<br/>Impacto = (C + I + D) / 3<br/>Puntaje = (Impacto + Probabilidad) / 2<br/>Nivel = [≥7: Crítico, ≥5: Alto, ≥3: Medio, <3: Bajo]
    VulnCtrl->>VulnRepo: save(vulnerabilidadCalculada)
    VulnRepo-->>VulnCtrl: Guardado exitoso
    VulnCtrl-->>Usuario: Redirección con vista actualizada del departamento
```

---

### 3.2. Flujo de Datos: Descubrimiento y Monitoreo de Red Local (WebSocket)

Muestra cómo interactúa el módulo de red con el sistema operativo Windows, el scheduler en segundo plano y el WebSocket en tiempo real:

```mermaid
sequenceDiagram
    autonumber
    actor Operador as Operador / UI (/network-monitoring)
    participant Scheduler as NetworkMonitoringScheduler
    participant MonSvc as NetworkMonitoringService
    participant DiscSvc as LocalNetworkDiscoveryService
    participant OS as Stack Red Windows (ARP / Ping / Route)
    participant WS as NetworkMonitorWebSocketHandler
    participant DB as Base de Datos (MySQL)

    rect rgb(240, 248, 255)
        note over Operador,DB: Escaneo de Red Local bajo demanda
        Operador->>DiscSvc: POST /api/network/discover
        DiscSvc->>OS: Obtiene interfaz IPv4 activa y máscara (/24)
        DiscSvc->>OS: Probes ICMP concurrentes (64 hilos, 2 pings)
        DiscSvc->>OS: Lee tabla ARP de Windows (Get-NetNeighbor) y default gateway
        DiscSvc->>DB: Actualiza network_detected_device (Marca offline a los ausentes)
        DiscSvc->>DB: Registra network_discovery_snapshot y network_discovery_event
        DiscSvc-->>Operador: Retorna Snapshot con dispositivos, MACs y Gateways
    end

    rect rgb(255, 250, 240)
        note over Scheduler,DB: Monitoreo Programado de Hosts Autorizados
        Scheduler->>MonSvc: Ejecución periódica (cada N segundos)
        MonSvc->>DB: Carga network_host y monitored_port activos
        MonSvc->>OS: Prueba alcance ICMP y conexión TCP a puertos configurados
        alt Cambio de estado (Online / Offline)
            MonSvc->>DB: Actualiza estado del host y crea network_event
            MonSvc->>WS: publish(NetworkEventDto)
            WS-->>Operador: Evento en vivo por WebSocket (/ws/network)
        end
    end
```

---

## 4. Diccionario de Datos: Estructura de Información

A continuación se resumen las tablas principales y sus relaciones dentro de TinyHat:

| Tabla / Entidad | Rol en el Flujo | Datos Clave |
| :--- | :--- | :--- |
| **`empresa`** | Entidad raíz del cliente auditado | `id_empresa`, `nombre`, `nit`, `sector` |
| **`auditoria`** | Proyectos de ciberseguridad / Pentesting | `id_auditoria`, `nombre`, `tipo_auditoria`, `estado`, `fecha_inicio`, `empresa_id` |
| **`area_auditable`** | Departamentos o activos auditados | `id_area`, `nombre`, `descripcion`, `id_auditoria` |
| **`vulnerabilidad`** | Hallazgos evaluados bajo escala TinyHat | `confidencialidad`, `integridad`, `disponibilidad`, `probabilidad`, `impacto`, `nivel_riesgo`, `cve`, `plan_mitigacion`, `id_area` |
| **`cve`** | Caché sincronizada del NVD con traducción | `codigo_cve`, `descripcion`, `puntaje_cvss`, `severidad`, `cwe`, `fecha_sincronizacion` |
| **`network_host`** | Hosts registrados/autorizados para monitoreo | `address` (IP), `name`, `status` (ONLINE/OFFLINE), `last_seen` |
| **`monitored_port`** | Puertos vigilados por host | `port`, `enabled`, `host_id` |
| **`network_detected_device`** | Dispositivos descubiertos en la subred | `ip_address`, `mac_address`, `hostname`, `status`, `detection_method`, `is_gateway` |
| **`network_scan`** | Registro de escaneos de puertos manuales | `start_time`, `end_time`, `status`, `host_id` |
| **`port_scan_result`** | Resultados individuales de puertos sondeados | `port`, `status` (OPEN/CLOSED/FILTERED), `scan_id` |

---

## 5. Resumen de Tecnologías y Protocolos de Comunicación

- **Frontend**: HTML5, Thymeleaf, Vanilla CSS, JavaScript asíncrono (`fetch`), WebSockets (`/ws/network`).
- **Backend**: Spring Boot 4.x / Java 21, Spring MVC, Spring Data JPA, Spring WebSocket, Spring Scheduler.
- **Base de Datos**: MySQL con migraciones versionadas gestionadas por **Flyway** (`V1` a `V16`).
- **Servicios Externos**: NIST NVD API 2.0 (REST JSON) y MyMemory Translation API.
- **Interacción con Sistema Operativo**: Comandos de red Windows (`ping.exe`, `Get-NetNeighbor`, `route print`), Sockets TCP raw y multidifusión mDNS/SSDP.
