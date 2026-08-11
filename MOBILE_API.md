# EXMWS Mail — Mobile API (Android)

Guía para integrar la app Android de **CORREO** con el backend EXMWS Mail.
La app **no toca IMAP/SMTP directamente** — todo va por esta API REST + FCM
para notificaciones push.

> **Alcance:** este doc es **solo la app de correo** (+ contactos y filtros, que
> son parte del correo). Calendario, Tareas, Notas, Contraseñas y Chat serán
> **apps móviles independientes**, cada una con su propio backend y doc — ver §9.

> **Verificado contra el código el 2026-06-09.** Esta revisión corrigió drift en
> §4.7 (mover usa `destination`, no `to`), §4.14 (carpetas son query params),
> §4.16 (borrador único, no lista) y §4.11 (búsqueda sin `page` + filtros), y
> documentó el captcha (§1.5), la mecánica real del push (§3.1) y endpoints
> útiles que faltaban (§4.17). Puntos que el dev DEBE leer antes de empezar:
> **§1.5 (captcha)** y **§3.1 (latencia del push)**.
>
> **Actualización 2026-06-14:** los contadores de `/folders` ahora se refrescan
> al instante tras cada acción (§4.1); `/sync` es barato cuando no hay cambios
> (§4.13); y el WS soporta `set_active_folder` para tiempo real en la carpeta
> abierta en foreground (§8). Son mejoras retrocompatibles — no rompen nada.
>
> **Actualización 2026-08-08 — revisión completa contra el código.** Corregido
> drift: borradores (§4.16 — ahora `multipart` con adjuntos y multi-ventana),
> body de `batch`/`prefetch` (§4.17 — array plano, no `{"uids":...}`), borrar
> desde Junk NO purga (§4.9), respuesta y errores de `/send` (§4.12), `/sync`
> responde al instante sin esperar (§4.13) y latencia real del push (§3.1 —
> webhook de Dovecot desde 2026-07-30). Nuevo: campos `color` / `answered_at` /
> `forwarded_at` / `references` en `EmailMessage` (§4.2), operaciones de hilo
> completo (§4.4), banderas de color (§4.18), recordatorios/followups (§4.19),
> carpetas compartidas (§4.20), IA (§4.21), RSVP de invitaciones (§4.22) y
> multi-cuenta `X-Account-Id` (§4.23).
>
> ⚠️ El backend tiene Swagger/OpenAPI **deshabilitado** en prod
> (`docs_url=None`), así que `/docs` y `/openapi.json` NO sirven la API (el
> dominio devuelve el SPA). Los schemas exactos de request/response viven en los
> modelos Pydantic del backend: `backend/app/schemas/` (user, device, contact).

---

## 0. Conceptos rápidos

| Concepto | Detalle |
|---|---|
| Base URL prod | `https://webmail.exmworkspace.com` |
| Auth | JWT en header `Authorization: Bearer <access_token>` |
| Access token TTL | **15 min** (cuando `client="mobile"` en login) |
| Refresh token TTL | **60 días**, rotación en cada uso |
| Push | Firebase Cloud Messaging (FCM) — el backend dispara push cuando llega correo nuevo |
| Content type | `application/json` salvo en `/send` que es `multipart/form-data` |

Todos los timestamps están en ISO 8601 UTC. Todos los IDs de mensaje (`uid`) son
strings — IMAP UIDs son numéricos pero el API los serializa como string.

---

## 1. Autenticación

### 1.1 Login

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "usuario@dominio.com",
  "password": "contraseña-imap",
  "client": "mobile"
}
```

**Respuesta 200:**
```json
{
  "access_token": "eyJhbGc...",
  "token_type": "bearer",
  "refresh_token": "rT5xY...",
  "expires_in": 900,
  "user": {
    "id": 1,
    "email": "usuario@dominio.com",
    "full_name": "Carlos Torres Hanna",
    "display_name": "Carlos",
    "phone": null,
    "mobile": "5548660929",
    "job_title": "CEO",
    "department": "Dirección",
    "birthday": "1984-01-06",
    "avatar_url": null
  }
}
```

**Errores:**
- `401` credenciales inválidas (el backend valida contra IMAP)

> ⚠️ **Sin `"client":"mobile"`** el endpoint devuelve solo `access_token` con TTL
> de 24h y `refresh_token: null` (modo web legacy). La app DEBE mandar
> `"client":"mobile"`.

### 1.2 Renovar token (rotación + theft detection)

Antes de que el access expire (idealmente con ~2min de margen):

```http
POST /api/auth/refresh
Content-Type: application/json

{ "refresh_token": "rT5xY..." }
```

**Respuesta 200:**
```json
{
  "access_token": "eyJ...",
  "token_type": "bearer",
  "refresh_token": "nuevo_refresh_token",
  "expires_in": 900
}
```

**IMPORTANTE — rotación**: cada `/refresh` retorna un **nuevo** `refresh_token`
y revoca el anterior. La app debe persistir el nuevo y **descartar** el viejo
inmediatamente.

**Errores:**
- `401 Invalid or revoked refresh token` — sucede en 3 casos:
  1. El token nunca existió
  2. Expiró (>60 días sin uso)
  3. **Theft detection**: alguien presentó un token revocado → el backend revoca
     toda la family y obliga a re-login. La app debe redirigir a la pantalla de
     login y borrar credenciales locales.

### 1.3 Logout

```http
POST /api/auth/logout
Content-Type: application/json
Authorization: Bearer <access_token>     ← opcional, el endpoint no exige auth

{ "refresh_token": "rT5xY..." }          ← opcional pero recomendado
```

Si se manda el `refresh_token`, queda revocado en DB. Idempotente — siempre `200`.

### 1.4 Datos del usuario logueado

```http
GET /api/auth/me
Authorization: Bearer <access_token>
```

Devuelve el mismo objeto `user` que `/login`.

### 1.5 Captcha (slider conductual) — manejo OBLIGATORIO

El backend protege `/login` con un **gate de captcha**: si la IP del cliente
acumuló **≥1 intento de login fallido reciente** (ventana de 10 min), el
siguiente `/login` responde:

```
401  { "detail": "captcha_required" }
```

A partir de ese momento, el `/login` SOLO acepta credenciales si además incluye
un `captcha_token` válido. La app DEBE detectar el detail `captcha_required` y
resolver el slider:

**Paso 1 — pedir un challenge:**
```http
POST /api/auth/captcha/challenge
→ 200  { "challenge_id": "abc123..." }
```

**Paso 2 — el usuario arrastra el slider; la app envía la traza de puntos:**
```http
POST /api/auth/captcha/verify
Content-Type: application/json

{
  "challenge_id": "abc123...",
  "duration_ms": 1240,
  "points": [
    { "x": 0,   "y": 31, "t": 0 },
    { "x": 12,  "y": 33, "t": 48 },
    { "x": 40,  "y": 30, "t": 96 },
    ...
    { "x": 268, "y": 32, "t": 1240 }
  ]
}
→ 200  { "captcha_token": "def456..." }   (single-use, TTL 5 min)
→ 400  { "detail": "Captcha failed" }     (traza no parece humana)
```

**Paso 3 — reintentar el login con el token:**
```json
POST /api/auth/login
{ "email": "...", "password": "...", "client": "mobile", "captcha_token": "def456..." }
```

**Heurísticas que valida el backend** (la app debe generar una traza realista —
no basta con dos puntos):
- `duration_ms` entre **250 y 8000 ms**
- **≥8 puntos** de `pointer` (un humano genera 15-50+)
- Varianza temporal entre eventos (no intervalos uniformes)
- Varianza vertical en `y` (no una línea perfectamente recta)
- El último `x` debe llegar al **≥90%** del recorrido

> 💡 Implementación nativa Android: un `View` con `onTouchEvent` que registre
> `(x, y, eventTime)` en cada `ACTION_MOVE` ya produce una traza válida — son los
> datos crudos del gesto real del dedo. NO sintetizar los puntos: el backend
> rechaza trazas demasiado limpias.
>
> El `captcha_token` es de **un solo uso** y caduca en 5 min. Si el login vuelve
> a fallar (password incorrecto), hay que pedir un challenge nuevo.

---

## 2. Registro de dispositivo (FCM)

### 2.1 Obtener el FCM token (en la app)

```kotlin
FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
    if (task.isSuccessful) {
        val fcmToken = task.result
        // Mandarlo al backend ↓
    }
}
```

### 2.2 Registrar el device

```http
POST /api/devices/register
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "fcm_token": "fcm-token-de-firebase",
  "platform": "android",
  "device_name": "Pixel 8 Pro",
  "app_version": "1.0.0"
}
```

**Respuesta 201:**
```json
{
  "id": 7,
  "platform": "android",
  "device_name": "Pixel 8 Pro",
  "app_version": "1.0.0",
  "last_seen_at": "2026-05-12T05:41:39.476639Z",
  "created_at": "2026-05-12T05:41:39.476639Z"
}
```

**Cuándo llamar:**
- Justo después del primer login exitoso
- Cuando Firebase rota el token (suscribirse a `onNewToken` en
  `FirebaseMessagingService`)
- En cada arranque de la app (es idempotente — upsert por `fcm_token`)

### 2.3 Listar mis devices

```http
GET /api/devices/
Authorization: Bearer <access_token>
```

Devuelve array de `DeviceResponse`. Útil para mostrar "Sesiones activas".

### 2.4 Eliminar device (logout móvil)

```http
DELETE /api/devices/{id}
Authorization: Bearer <access_token>
```

Llamar antes de hacer logout para que el server deje de mandar push a este
dispositivo. Si la app es desinstalada y el token deja de ser válido, FCM
notificará al server en el próximo intento de push y se borrará automáticamente.

---

## 3. Estructura del push FCM

Cuando llega un correo nuevo al INBOX del usuario, el backend manda:

```json
{
  "notification": {
    "title": "Carlos Torres",
    "body": "Re: Propuesta comercial Q2\nHola, te confirmo la reunión del..."
  },
  "data": {
    "type": "new_email",
    "account_id": "1",
    "folder": "INBOX",
    "uid": "1234",
    "thread_id": "abc123..."
  },
  "android": { "priority": "high" }
}
```

**Sugerencias para la app:**
- En `onMessageReceived`, parsear `data.type`. Para `"new_email"`, hacer un
  fetch a `GET /api/emails/messages/{uid}?folder={folder}` para tener el
  detalle pre-cargado cuando el usuario toque la notif.
- Deep link al hilo: `intent.putExtra("uid", data.uid)` + `data.thread_id`
- Si la app está en foreground, opcionalmente actualizar la lista en pantalla
  en lugar de mostrar la notif del sistema.

### 3.1 Cómo se dispara el push (latencia y garantías)

Desde 2026-07-30 el **sync** del correo nuevo es instantáneo para TODAS las
cuentas: Dovecot notifica al backend en cuanto entra un correo (webhook interno
`/api/internal/push`) y el INBOX se sincroniza al momento — ya no depende de que
el usuario tenga sesión web abierta. Sobre eso hay **dos caminos** que disparan
la notificación, y la app no necesita distinguirlos:

1. **Tiempo real (IDLE/WS)** — cuando el usuario tiene además una sesión **web**
   abierta, el push sale en **~1-3s**.
2. **Cron FCM (mobile)** — para usuarios que **solo** usan la app, un job corre
   **cada minuto** (en el segundo :30) y dispara el FCM de lo que el sync ya
   bajó (watermark por cuenta — nunca duplica). Latencia típica **~5-60s**.

> Implicación para la app: el push de correo **no siempre es instantáneo** (puede
> tardar hasta ~60s si el usuario solo usa el móvil). Para la sensación de
> "tiempo real" al abrir la app o hacer pull-to-refresh, llamar
> `POST /api/emails/sync?folder=INBOX` (§4.13). Cada correo se notifica **una
> sola vez**; si el usuario ya lo leyó (p. ej. en otra sesión), no se reenvía.

---

## 4. Endpoints de correo

Todos requieren `Authorization: Bearer <access_token>`. Errores comunes:
- `401` — token expirado/inválido (llamar `/refresh`)
- `403` — operación no permitida
- `404` — recurso no existe
- `422` — body/query inválido
- `500` — error en backend (red al IMAP, etc.)

### 4.1 Carpetas

```http
GET /api/emails/folders
```

```json
[
  { "name": "INBOX", "display_name": "Bandeja de entrada", "message_count": 1234, "unseen_count": 17,
    "is_shared": false, "shared_owner": null, "shared_rights": null },
  { "name": "Sent",  "display_name": "Enviados",            "message_count":  450, "unseen_count":  0, ... },
  { "name": "Trash", "display_name": "Papelera",            "message_count":   12, "unseen_count":  0, ... },
  ...
]
```

> `is_shared` / `shared_owner` / `shared_rights` _(añadidos 2026-07-31)_: soporte
> de carpetas compartidas — ver §4.20.

> **Contadores (`unseen_count` / `message_count`):** se calculan del cache local
> del backend — **no hay round-trip a IMAP**, así que `/folders` responde rápido
> incluso con buzones de miles de correos. Se actualizan de forma **fiable e
> inmediata** tras leer / marcar no-leído / mover / borrar / marcar spam / vaciar
> una carpeta, y tras un `POST /api/emails/sync`. Es seguro volver a pedir
> `/folders` justo después de una acción para refrescar los badges (antes de
> 2026-06-14 había un lag de hasta ~60s en la carpeta destino; ya está resuelto).

### 4.2 Listar mensajes

```http
GET /api/emails/messages?folder=INBOX&page=1&per_page=50
```

**Query params:**
- `folder` (default `INBOX`)
- `page` (1..N, default `1`)
- `per_page` (1..100, default `50`) — móvil puede usar 25-30 para ahorrar bandwidth
- `category` (opcional) — `Personal`, `Comercial`, `Social`, `Notificación`,
  `Transaccional`, `Urgente`
- `color` (opcional) — filtra por bandera de color: `red`, `orange`, `green`,
  `blue`, `purple` (ver §4.18)

Devuelve `list[EmailMessage]`:
```json
[
  {
    "uid": "1234",
    "message_id": "<abc@server>",
    "from_address": "juan@empresa.com",
    "from_name": "Juan Pérez",
    "to": ["yo@dominio.com"],
    "cc": [],
    "subject": "Reunión",
    "date": "2026-05-12T10:30:00Z",
    "snippet": "Hola, te confirmo...",
    "folder": "INBOX",
    "is_read": false,
    "has_attachments": true,
    "is_pinned": false,
    "color": null,
    "answered_at": null,
    "forwarded_at": null,
    "references": "<id-abuelo@x> <id-padre@x>",
    "thread_id": "thread-abc",
    "thread_count": 3,
    "ia_category": "Comercial"
  }
]
```

> **Campos añadidos después de 2026-06** (siempre presentes, pueden ser `null`):
> `color` — bandera de color (§4.18); `answered_at` / `forwarded_at` — ISO
> timestamp de cuándo el usuario respondió/reenvió ESTE correo (para banners
> estilo Outlook "Respondiste el..."); `references` — cadena References completa
> del correo, **úsala al responder** (§4.12) para que el hilo no se parta en
> respuestas-a-respuestas.

### 4.3 Detalle del mensaje

```http
GET /api/emails/messages/{uid}?folder=INBOX
```

Hereda los campos de `EmailMessage` y añade:
```json
{
  ...campos_de_email_message,
  "body_html": "<html>...</html>",
  "body_text": "Versión texto plano...",
  "attachments": [
    { "index": 0, "filename": "doc.pdf", "content_type": "application/pdf", "size": 124567 }
  ]
}
```

> ⚠️ El backend marca el mensaje como leído **solo** vía
> `POST /api/emails/messages/{uid}/read` (después de >3s de visualización en
> web). En móvil decide tú la heurística.

### 4.4 Hilo completo

```http
GET /api/emails/thread?thread_id=abc&folder=INBOX
```

Devuelve `list[EmailMessage]` ordenados por fecha ASC. La web muestra el más
reciente arriba expandido — replica ese patrón.

**Operaciones sobre el hilo completo** _(añadidas 2026-07)_ — la tarjeta de la
lista representa un HILO: borrar/mover desde la lista debe operar sobre TODOS
sus mensajes, no solo el `uid` visible (que es solo el representante):

```http
POST   /api/emails/thread/read?thread_id=abc                      Marca todo el hilo leído
POST   /api/emails/thread/move?thread_id=abc&destination=Archive  Mueve todo el hilo
DELETE /api/emails/thread?thread_id=abc&folder=INBOX              A Papelera (purga si folder=Trash)
POST   /api/emails/thread/restore?thread_id=abc                   Restaura desde Papelera
```

- `thread/move` acepta `source_folder` opcional: mover solo los mensajes de esa
  carpeta (p.ej. restaurar desde Spam sin tocar las copias en Sent).
- `thread/restore` devuelve cada mensaje a su carpeta natural: los enviados por
  el usuario → `Sent`, los recibidos → `INBOX` (estilo Gmail).
- `thread/move` y `DELETE /thread` sincronizan la carpeta destino ANTES de
  responder (pueden tardar 1-3s) — al volver, el destino ya está consistente.

### 4.5 Marcar leído / no leído

```http
POST /api/emails/messages/{uid}/read?folder=INBOX
POST /api/emails/messages/{uid}/unread?folder=INBOX
```

### 4.6 Pin / unpin

```http
POST   /api/emails/messages/{uid}/pin?folder=INBOX
DELETE /api/emails/messages/{uid}/pin?folder=INBOX
```

### 4.7 Mover / copiar entre carpetas

```http
POST /api/emails/messages/{uid}/move?folder=INBOX&destination=Archive
POST /api/emails/messages/{uid}/copy?folder=INBOX&destination=Archive
```

> ⚠️ El parámetro se llama **`destination`** (no `to`). `move` borra el original;
> `copy` lo conserva. `destination` puede ser `INBOX`, `Trash`, `Archive`, `Sent`
> o cualquier carpeta personal.

### 4.8 Marcar como spam

```http
POST /api/emails/messages/{uid}/spam?folder=INBOX
```

Mueve a `Junk`.

### 4.9 Eliminar

```http
DELETE /api/emails/messages/{uid}?folder=INBOX
```

Si la carpeta origen NO es `Trash`, lo mueve a `Trash` — **incluido desde
`Junk`** (borrar spam NO purga, va a Papelera). Solo si ya está en `Trash` lo
elimina permanentemente (UID EXPUNGE de ese mensaje). Tras un borrado el backend
sincroniza la Papelera en background — sus contadores en `/folders` tardan
~1-2s en reflejarlo.

### 4.10 Adjuntos

**Descargar adjunto del mensaje:**
```http
GET /api/emails/messages/{uid}/attachment/{index}?folder=INBOX
```
Devuelve el archivo binario con `Content-Type` y `Content-Disposition` correctos.

**Imágenes CID (inline en HTML del correo):**
```http
GET /api/emails/messages/{uid}/cid/{content_id}?folder=INBOX
```
Útil cuando el `body_html` referencia `<img src="cid:xxx">`. Reemplazar esos src
por esta URL al renderizar el WebView.

### 4.11 Búsqueda full-text

```http
GET /api/emails/search?q=propuesta&folder=INBOX
```

`q` mínimo **2 caracteres**. `folder` por defecto `INBOX` (no hay paginación en
este endpoint; devuelve hasta 50 resultados, cacheados 120s). Hot path 100% en
PostgreSQL FTS, con fallback a IMAP SEARCH.

**Filtros especiales** (se escriben dentro del propio `q`):
- `is:unread` / `is:read` — por estado de lectura
- `has:attachment` — solo con adjuntos
- `after:YYYY-MM-DD` / `before:YYYY-MM-DD` — rango de fechas

Ej: `q=propuesta is:unread after:2026-01-01`

### 4.12 Enviar correo

```http
POST /api/emails/send
Authorization: Bearer <access_token>
Content-Type: multipart/form-data

Form fields:
  to:           "destinatario1@x.com,destinatario2@y.com"
  cc:           "copy@x.com"            (opcional)
  bcc:          ""                       (opcional)
  subject:      "Asunto"
  body:         "<p>HTML del correo</p>"
  is_html:      "true"
  in_reply_to:  ""                       (opcional, Message-Id del original — RESPUESTA)
  references:   ""                       (opcional, headers de threading)
  forward_of:   ""                       (opcional, Message-Id del original — REENVÍO)
  files:        [archivo1, archivo2]     (opcional, repetir el campo por cada uno)
```

> ⚠️ `/send` es **multipart**, no JSON, para soportar archivos adjuntos.

**Respuesta 200:** `{"message": "Correo enviado"}`

Para **responder**: `in_reply_to` = `message_id` del original y `references` =
campo `references` del original (viene en `EmailMessage`, §4.2) + su
`message_id` al final. Para **reenviar**: `forward_of` = `message_id` del
original. Con esos campos el backend marca el original como contestado/reenviado
(`answered_at`/`forwarded_at`, flag IMAP `\Answered`).

**Errores que la app debe manejar** (el `detail` ya viene en español, apto para
mostrar directo):
- `422` — ningún destinatario válido
- `400` — una dirección con caracteres no-ASCII (p.ej. `Ortuño@...`, que Outlook
  fabrica a partir del display name); el `detail` **nombra la dirección** para
  que el usuario la corrija
- `429` — límite de envíos por hora superado (trae header `Retry-After`)
- `503` — servicio de envío momentáneamente saturado; reintentar en unos segundos
- `500` — el `detail` incluye un **código de referencia** de 8 caracteres;
  mostrarlo permite a soporte ubicar el error exacto en los logs

### 4.13 Sync manual (pull-to-refresh)

```http
POST /api/emails/sync?folder=INBOX
```

**Encola** un sync IMAP del folder y responde al instante con
`{"message": "Sync started"}` — NO espera a que termine. Para pull-to-refresh:
llamar `/sync`, esperar ~1-2s y refetchear `GET /messages`. No es estrictamente
necesario porque el push FCM ya notifica, pero útil para "deslizar para
refrescar".

> **Es barato cuando no hay cambios:** el backend usa un fast-path — si el nº de
> mensajes y el `UIDNEXT` del folder no cambiaron desde el último sync, no
> descarga nada (solo un `STATUS` de metadata). Puedes llamarlo en cada
> pull-to-refresh sin penalizar al servidor IMAP. Sin `folder`, sincroniza la
> cuenta completa (más costoso); para pull-to-refresh pasa siempre `?folder=`.

### 4.14 Carpetas: crear / renombrar / vaciar / borrar

> ⚠️ Estos endpoints reciben los datos como **query params**, NO como body JSON.

```http
POST   /api/emails/folders?name=Proyectos
PUT    /api/emails/folders/rename?old_name=X&new_name=Y
POST   /api/emails/folders/empty?name=Trash
DELETE /api/emails/folders?name=Proyectos
```

Las carpetas de sistema (`INBOX`, `Sent`, `Drafts`, `Archive`, `Trash`, `Junk`,
`Spam`) están protegidas contra rename.

> `folders/empty` es **asíncrono** _(desde 2026-07)_: responde en ~0.1s, los
> contadores bajan al instante y el EXPUNGE IMAP corre en background (antes una
> Papelera grande colgaba la request ~45s). Mientras la purga corre, la carpeta
> no se re-sincroniza — los correos no "reaparecen".

### 4.15 Cuota

```http
GET /api/emails/quota
```

Devuelve uso de almacenamiento del buzón (bytes usados/totales).

### 4.16 Borradores

> ⚠️ **Cambió a `multipart/form-data`** _(2026-06-19, para soportar adjuntos)_ —
> ya NO es JSON. Y ahora hay **un borrador por ventana de redacción** vía
> `client_draft_id` (la web soporta 2 composers simultáneos), no uno global
> por usuario.

**Guardar / reemplazar:**
```http
POST /api/emails/drafts
Content-Type: multipart/form-data

Form fields:
  to, cc, bcc, subject, body          (strings, igual que /send)
  in_reply_to, references             (opcional, threading)
  client_draft_id: "abc123"           (ID estable generado por la app, POR borrador/ventana)
  attachments_mode: "keep"|"replace"  (default keep, ver abajo)
  prev_uid: "123"                     (uid del borrador anterior a reemplazar, si existe)
  files: [archivo1, ...]              (solo con attachments_mode=replace)
```

**Respuesta 200:** `{"message": "Draft saved", "uid": "456"}` — guardar ese
`uid` como `prev_uid` del siguiente save.

**Adjuntos — dos modos** (para no re-subir archivos pesados en cada autosave):
- `replace`: la app sube el set **completo** de adjuntos en `files` (set vacío =
  borrador sin adjuntos). Usarlo SOLO cuando el usuario agrega/quita adjuntos.
- `keep` (default): NO sube archivos — el backend arrastra los adjuntos del
  borrador anterior (`prev_uid`) y solo actualiza cabeceras + cuerpo. Usarlo
  para el autosave periódico.

**Consultar / borrar:**
```http
GET    /api/emails/drafts?client_draft_id=abc123
       → { "uid": "123" }                       borrador rastreado
       → { "uid": null, "fallback": {...} }     el último save falló en IMAP; fallback trae el contenido para recuperarlo
       → null                                   no hay borrador
DELETE /api/emails/drafts?client_draft_id=abc123&uid=123
```

En `DELETE`, pasar el `uid` del borrador que ESA ventana editaba (tiene
prioridad y borra exactamente ese); sin `uid` cae al último rastreado en Redis.
Llamarlo tras enviar o descartar.

### 4.17 Endpoints adicionales útiles (existen pero no estaban documentados)

| Endpoint | Para qué |
|---|---|
| `POST /api/emails/messages/batch?folder=INBOX` body `["1","2",...]` (array JSON **plano**, NO `{"uids":...}`) | **Trae varios detalles (body+adjuntos) en UNA llamada.** Ideal para pre-cargar/offline en móvil sin N requests. Devuelve `list[EmailDetail]`. |
| `GET /api/emails/category-counts?folder=INBOX` | Conteo de hilos por categoría IA (para badges/filtros). |
| `GET /api/emails/messages/{uid}/headers?folder=INBOX` | Headers crudos del correo. |
| `GET /api/emails/messages/{uid}/source?folder=INBOX` | Fuente RFC822 completa ("ver original"). |
| `POST /api/emails/messages/{uid}/attachment/{index}/save-to-cloud?folder=INBOX&subfolder=...&overwrite=false` | Guarda un adjunto directo al EXMWS Cloud del usuario. |
| `GET /api/emails/attachments/browse?page=1&per_page=200&search=...` | Explorador de TODOS los adjuntos del buzón (excluye Junk/Trash). |
| `POST /api/emails/prefetch?folder=INBOX` body `["1","2",...]` (array plano) | Pre-cachea bodies en background (no devuelve los correos). |

### 4.18 Banderas de color _(añadido 2026-06)_

Bandera de color por correo, sincronizada entre clientes vía keyword IMAP
(`$label1..$label5` — compatible con Thunderbird):

```http
PUT /api/emails/messages/{uid}/color?folder=INBOX&color=red    asignar
PUT /api/emails/messages/{uid}/color?folder=INBOX              quitar (sin color)
```

Valores: `red`, `orange`, `green`, `blue`, `purple`. El color asignado viene en
el campo `color` de `EmailMessage`, y la lista se puede filtrar con
`GET /messages?color=red` (§4.2).

### 4.19 Recordatorios de correo (followups) _(añadido 2026-07)_

"Recuérdame este correo el día X". Al vencer, el backend notifica por WS
(evento `followup_due`), Web Push del navegador y un **correo de recordatorio
al INBOX**. La app móvil NO recibe un FCM propio del followup — pero el correo
de recordatorio que llega al INBOX sí dispara el FCM normal de correo nuevo
(§3), así que la notificación llega igual.

```http
GET    /api/followups                Lista de seguimientos
POST   /api/followups                { "folder": "INBOX", "uid": 123, "due_at": "2026-08-15T09:00:00Z", "note": "..." }
PUT    /api/followups/{id}           { "due_at": "...", "note": "..." }  (ambos opcionales)
POST   /api/followups/{id}/done      Marcar hecho
DELETE /api/followups/{id}
```

⚠️ Aquí `uid` es **int**, no string (a diferencia del resto de la API). El
`POST` hace upsert por `(folder, uid)`: crear otro sobre el mismo correo
actualiza el existente. `note` opcional, máx 2000 chars.

### 4.20 Carpetas compartidas (ACL Dovecot) _(añadido 2026-07-31)_

`GET /folders` puede incluir carpetas del namespace compartido (carpetas de OTRO
usuario compartidas conmigo): traen `shared_owner` (email del dueño) y
`shared_rights` (derechos IMAP). Las propias traen `is_shared: true` si el
usuario las compartió. **Escribir** (borrar/mover/marcar) en una compartida de
solo-lectura devuelve **`403`** — la app debe deshabilitar esas acciones si
`shared_rights` no incluye escritura.

Gestión de carpetas propias:
```http
GET    /api/emails/folders/shares?name=Proyectos       Con quién está compartida
PUT    /api/emails/folders/share                       { "folder": "Proyectos", "grantee": "user@dominio", "permission": "read"|"write" }
DELETE /api/emails/folders/share?folder=Proyectos&grantee=user@dominio
```

### 4.21 IA — resumen, redacción, traducción _(añadido 2026-06/07)_

Endpoints stateless (la app manda el contenido; el backend llama a DeepSeek):

```http
POST /api/emails/summarize
  { "subject": "...", "messages": [ { "sender": "...", "date": "...", "body": "texto plano" }, ... ] }
  → resumen del hilo en español

POST /api/emails/ai/draft
  { "subject": "...", "messages": [...contexto si es respuesta...], "my_draft": "lo que llevo escrito", "is_reply": true }
  → 3 propuestas de redacción

POST /api/emails/ai/translate            { "text": "...", "language": "inglés" }
POST /api/emails/ai/translate-segments   { "segments": ["frag 1", "frag 2", ...], "language": "español" }
```

`translate-segments` es para traducción inline del cuerpo renderizado: la app
extrae los **nodos de texto** (nunca HTML), los manda en orden y reinserta cada
fragmento traducido en su sitio.

### 4.22 Invitaciones de calendario — RSVP _(añadido 2026-07)_

Si un correo trae un `.ics` adjunto (`METHOD:REQUEST`), la web muestra una
tarjeta Aceptar / Pendiente / Rechazar. El parseo del `.ics` es del cliente
(descargarlo vía §4.10); la respuesta iTIP al organizador se envía con:

```http
POST /api/emails/calendar/reply
{
  "organizer_email": "org@externo.com",
  "organizer_name": "Nombre",              (opcional)
  "summary": "Junta de proyecto",
  "ical_uid": "uid-del-evento@dominio",
  "status": "accepted" | "tentative" | "declined",
  "start_at": "2026-08-15T16:00:00Z",      (opcional)
  "end_at": "2026-08-15T17:00:00Z",        (opcional)
  "sequence": 0                            (opcional)
}
```

El REPLY sale **desde el buzón del usuario** (como exige iTIP) y queda copia en
Sent.

### 4.23 Multi-cuenta / buzones auxiliares _(añadido 2026-06)_

Un usuario puede tener buzones auxiliares (cuentas IMAP adicionales). TODOS los
endpoints de correo aceptan el header **`X-Account-Id: <id>`** para operar sobre
una cuenta específica; sin el header se usa la cuenta principal. El backend
valida propiedad (un id ajeno cae a la default — anti-IDOR).

```http
GET    /api/accounts/          Cuentas del usuario (id, email, display_name, ...)
POST   /api/accounts/          Alta de cuenta IMAP adicional
DELETE /api/accounts/{id}
```

> Si la app implementa selector de cuenta: TODO el estado local (lista, cache,
> borradores) debe estar particionado por cuenta — `(folder, uid)` NO es único
> entre cuentas.

---

## 5. Contactos (opcional para v1)

```http
GET    /api/contacts                Listar
POST   /api/contacts                Crear (body con name, email, phone, etc)
GET    /api/contacts/{id}           Detalle
PUT    /api/contacts/{id}           Editar
DELETE /api/contacts/{id}           Borrar
GET    /api/contacts/suggest?q=...  Autocompletar al escribir destinatario
GET    /api/contacts/corporate/list Directorio de la empresa (mismo dominio)

GET    /api/contacts/count          Conteos (total, manual, importados, favoritos)
GET    /api/contacts/groups         Grupos con conteo
GET    /api/contacts/groups/detail  Grupos con detalle (id, nombre, dominio, color)
POST   /api/contacts/{id}/favorite  Toggle favorito
POST   /api/contacts/import-from-emails  Auto-importa contactos del historial de correo
```

---

## 6. Manejo de errores recomendado

```kotlin
// Pseudocódigo Kotlin / OkHttp interceptor
when (response.code) {
    401 -> {
        if (response.body?.string()?.contains("revoked") == true) {
            // Theft detection o refresh expirado → forzar re-login
            clearTokens()
            navigateToLogin()
        } else {
            // Access token expirado → intentar refresh
            val newPair = refresh(refreshToken)
            if (newPair != null) {
                saveTokens(newPair)
                retryRequest()
            } else {
                clearTokens()
                navigateToLogin()
            }
        }
    }
    422 -> showValidationError(response.body)
    500..599 -> showRetryDialog()
}
```

**Recomendación:** implementar el refresh como un OkHttp `Authenticator` para
que se dispare automáticamente en cualquier 401 sin que cada llamada tenga que
saber del flujo.

> _(2026-07)_ El backend devuelve el `detail` de los errores **en español y
> apto para mostrar al usuario final** (p.ej. límites de envío, direcciones
> inválidas, servicio saturado). La app puede mostrar `detail` directamente
> cuando exista, con fallback a un mensaje genérico por código de estado —
> mismo patrón que usa la web.

---

## 7. Flujo de la app — resumen

```
1. PRIMER ARRANQUE
   ├─ Pantalla login → POST /api/auth/login { client: "mobile" }
   │   └─ Si 401 captcha_required → slider (§1.5) → reintentar con captcha_token
   ├─ Guardar tokens en EncryptedSharedPreferences
   ├─ Pedir token FCM → POST /api/devices/register
   └─ Conectar a Firebase para recibir push

2. CADA REQUEST
   ├─ Checar expires_in del access; si <2min, hacer refresh primero
   └─ Authorization: Bearer <access_token>

3. REFRESH AUTOMÁTICO
   ├─ POST /api/auth/refresh con refresh_token
   ├─ Si 200: guardar nuevo par, descartar viejo (rotación)
   └─ Si 401: borrar tokens, ir a login

4. NUEVO CORREO
   ├─ FCM dispara onMessageReceived
   ├─ data.type == "new_email" → pre-fetch /api/emails/messages/{uid}
   └─ Mostrar notif del sistema con deep-link al hilo

5. ENVIAR CORREO
   ├─ POST /api/emails/send (multipart)
   └─ Si hay borrador local, eliminar tras éxito

6. LOGOUT
   ├─ DELETE /api/devices/{id}            (deja de recibir push)
   ├─ POST /api/auth/logout { refresh_token }
   └─ Borrar tokens locales
```

---

## 8. WebSocket (opcional, NO recomendado para móvil)

El backend expone `/api/ws` con eventos `new_email`, `sync_complete`, etc.,
para la web. **No usar en móvil** — Android mata las conexiones largas en
background y drena batería. FCM es la respuesta correcta.

Si quisieras tiempo-real solo mientras la app está en foreground (por ejemplo,
chat en vivo), conectar al WS solo en `onResume()` y cerrarlo en `onPause()`.

Conecta con el JWT como query param: `wss://<host>/api/ws?token=<access_token>`.
Por defecto recibirás eventos de INBOX. Si además quieres tiempo-real sobre la
**carpeta que el usuario está viendo** (p. ej. está en Papelera o en un folder
custom), envía por el WS al abrir esa pantalla:

```json
{ "type": "set_active_folder", "folder": "Trash" }
```

El backend pone un IMAP IDLE sobre esa carpeta (además de INBOX) y empuja
`new_email` / `email_deleted` / `sync_complete` al instante cuando cambia — útil
para reflejar en vivo borrados/llegadas hechos desde otro cliente. Reenvía el
mensaje cada vez que el usuario cambie de carpeta (sustituye a la anterior, así
nunca hay más de ~2 carpetas vigiladas por cuenta). _Añadido 2026-06-14._

---

## 9. Alcance: qué cubre este doc y qué NO

Este doc cubre **solo la app de CORREO**. Los demás módulos serán **apps móviles
independientes**, cada una contra su propio backend y con su propia documentación
de API — **no se integran en la app de correo**:

| Módulo | App móvil independiente | Backend / API | Doc |
|---|---|---|---|
| Calendario | sí | PIM API `/pim/calendars`, `/pim/events` (port 8002) | `pim-api/MOBILE_API.md` |
| Tareas | sí | PIM API `/pim/tasks` | `pim-api/MOBILE_API.md` |
| Notas | sí | PIM API `/pim/notes` | `pim-api/MOBILE_API.md` |
| Contraseñas | sí | PIM API `/pim/passwords` | `pim-api/MOBILE_API.md` |
| Chat | sí | Messenger `/chat/*` (Node.js, auth propia vía token-login del correo) | `messenger/MOBILE_API.md` |

> Nota de auth para las apps PIM/Chat: comparten el SSO del correo. La PIM API
> valida el mismo JWT; el messenger hace `token-login` con el JWT del correo y
> emite su propio token (ver flujo en el `CLAUDE.md` del proyecto). Cada app
> deberá manejar su propio registro de device/push si quiere notificaciones.

**SÍ forman parte de la app de correo** (viven en el backend de correo y son
intrínsecos a redactar/gestionar correo), documentados arriba o disponibles:

| Endpoint | Para qué | Sección |
|---|---|---|
| `/api/contacts/*`, `/api/contacts/groups/*` | Agenda — autocompletar destinatarios, importar del historial, directorio corporativo | §5 |
| `/api/sieve/*` | Filtros server-side (reglas sobre el correo entrante) | — (no crítico v1) |

---

## 10. Cambios respecto a la API web

| Concepto | Web | Móvil |
|---|---|---|
| Auth | Cookie HttpOnly `exmws_session` (Domain=.exmworkspace.com, SSO) | JWT Bearer |
| Token TTL | 24h, sin refresh | 15min access + 60d refresh con rotación |
| Push real-time | WebSocket | FCM |
| Theft detection | No aplica | Sí: replay de refresh revocado → family revoke |
| Endpoint diff | Login sin `client` | Login con `client:"mobile"` |
| Endpoints nuevos | — | `/api/auth/refresh`, `/api/devices/*` |

El resto de endpoints (`/api/emails/*`, `/api/contacts/*`, attachments) son
idénticos entre web y móvil.

---

## 11. Protecciones del backend que la app debe respetar

Desde 2026-05-13 el backend tiene capas de seguridad activas. La app no las
ve directamente pero debe entender cómo se comportan para manejar errores:

### 11.1 Rate limit en `/api/auth/login` y `/api/auth/register`

| Concepto | Valor |
|---|---|
| Límite | 10 req/min por IP, burst de 5 |
| Respuesta al exceder | `429 Too Many Requests` |
| Ventana | 1 minuto |

La app debe:
- **No reintentar automáticamente** un login fallido. Si el usuario teclea
  mal el password, mostrar el error y esperar a que él reintente manualmente.
- Si recibe `429`, mostrar "Demasiados intentos, espera 1 minuto" y bloquear
  el botón de login durante 60s.
- Si recibe `401 {"detail":"captcha_required"}`, mostrar el slider y resolver
  el captcha **antes** de reintentar — ver **§1.5**. Esto pasa tras el primer
  intento fallido desde esa IP, así que es un camino común, no un caso borde.

### 11.2 fail2ban: ban tras 5 fallos en 10min

Si la IP del cliente acumula 5 respuestas `401` o `429` en `/api/auth/login`
o `/api/auth/register` durante 10 minutos, la IP queda **baneada por 30 min**
a nivel iptables: TODAS las requests (incluso a otros endpoints) fallan con
timeout de TCP.

Implicación para la app:
- Si después de un par de intentos fallidos el siguiente intento da timeout,
  probablemente la IP del usuario está baneada. Sugerir cambiar de red
  (WiFi → datos móviles) o esperar 30 min.
- Esto NO afecta a IPs internas (VPC AWS), solo a clientes públicos.

### 11.3 Login attempts logging

Cada intento de login (éxito o fallo) queda registrado en la tabla
`login_attempts` con `(email, ip, user_agent, success, failure_reason,
created_at)`. Esto es para auditoría/forensics — la app no interactúa con
esto, pero es útil saber que el header `User-Agent` que mande la app
quedará registrado. Recomendado: enviar un UA descriptivo del estilo:

```
User-Agent: EXMWS-Mobile/1.4.2 (Android 14; Pixel 8)
```

para que un análisis posterior pueda distinguir requests de la app vs
requests de scraping.

### 11.4 CORS y Host header

- El backend solo acepta requests con `Host: webmail.exmworkspace.com` (o
  subdominios `*.exmworkspace.com`). Si la app construye URLs propias para
  pegarle a una IP, el `Host` igual debe ser ese (HTTP/1.1 estándar lo hace
  automático cuando usas `https://webmail.exmworkspace.com`).
- CORS solo permite Origins `https://webmail.exmworkspace.com`,
  `https://meet.exmworkspace.com`, `https://cloud.exmworkspace.com`.
  Las apps nativas no envían `Origin` así que esto no las afecta, pero un
  WebView embebido sí lo enviaría — si la app usa WebView, asegurarse de
  cargar páginas desde uno de esos orígenes.

### 11.5 Recomendaciones de almacenamiento de tokens

| Plataforma | Dónde guardar refresh_token |
|---|---|
| Android | EncryptedSharedPreferences o Keystore |
| iOS | Keychain |
| Windows | Credential Manager o DPAPI |

**Nunca** guardar refresh tokens en almacenamiento plano (SharedPreferences
sin encrypt, NSUserDefaults, archivos en disco). Si el device se ve
comprometido y un atacante extrae el refresh token, válido por 60 días.

### 11.6 Flujo end-to-end recomendado

```
1. Usuario abre la app
2. ¿Hay refresh_token guardado?
   ├─ Sí → POST /api/auth/refresh
   │       ├─ 200 → guardar nuevo par, app lista
   │       └─ 401 → borrar credenciales, ir a pantalla de login
   └─ No → pantalla de login

3. Login con email+password:
   POST /api/auth/login con {"client":"mobile"}
   ├─ 200 → guardar (access, refresh), app lista
   ├─ 401 → mostrar "credenciales inválidas"
   └─ 429 → mostrar "muchos intentos, espera"

4. Durante uso normal, antes de cada request:
   ¿access_token expira en < 2min?
   └─ Sí → POST /api/auth/refresh primero, luego la request
```

Este flujo permite que el access TTL pueda bajarse en el futuro
(de 15min a 5min) sin requerir cambios en la app: la rotación
proactiva ya cubre el caso.
