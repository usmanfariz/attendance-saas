# Attendance SaaS — Absensi Karyawan Multi-Tenant

REST API multi-tenant untuk absensi karyawan. Satu instance aplikasi melayani
banyak perusahaan (tenant); data setiap perusahaan terisolasi penuh.

**Status: Phase 1–6 selesai.** Seluruh fase pada spesifikasi sudah dikerjakan:
fondasi proyek dan multi-tenant security, master data karyawan, sistem shift
dan absensi (termasuk shift malam lintas hari), geofencing, cuti dan koreksi
absensi dengan approval, dashboard dan laporan, audit log, serta subscription,
batas per plan, dan arsitektur billing.

> **Billing tidak terhubung ke payment gateway mana pun.** Yang dibangun adalah
> arsitekturnya: katalog plan, langganan, penegakan kuota, penerbitan tagihan,
> dan siklus penagihan. Pelunasan dicatat lewat satu endpoint yang menjadi titik
> sambung bila penyedia pembayaran dipasang nanti. Lihat bagian 9.17.

---

## 1. Requirement

| Kebutuhan | Versi |
|---|---|
| Java (JDK) | 21 |
| Maven | 3.9+ |
| MySQL | 8.0 |
| Docker / Docker Compose | opsional, untuk menjalankan seluruh stack |

Tanpa Docker Anda hanya perlu JDK 21, Maven, dan sebuah instance MySQL 8.

---

## 2. Installation

```bash
git clone <repository-url>
cd attendance-saas
cp .env.example .env      # lalu isi nilai aslinya
```

Build dan jalankan test:

```bash
mvn clean verify
```

---

## 3. Configuration

Seluruh kredensial dibaca dari environment variable — tidak ada yang di-hardcode.

| Variable | Default | Keterangan |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` atau `prod` |
| `DB_HOST` | `localhost` | Host MySQL |
| `DB_PORT` | `3306` | Port MySQL |
| `DB_NAME` | `attendance_saas` | Nama database |
| `DB_USERNAME` | `attendance` | User database |
| `DB_PASSWORD` | `attendance` | Password database |
| `JWT_SECRET` | dev-only | **Wajib diganti di production**, minimal 64 karakter |
| `JWT_EXPIRATION` | `60` | Umur access token (menit) |
| `JWT_REFRESH_EXPIRATION_DAYS` | `14` | Umur refresh token (hari) |
| `SERVER_PORT` | `8080` | Port HTTP aplikasi |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,...` | Daftar origin, dipisah koma |
| `SWAGGER_ENABLED` | `false` di prod | Aktifkan Swagger UI pada profil `prod` |

Generate `JWT_SECRET` yang aman:

```bash
openssl rand -base64 64 | tr -d '\n'
```

File konfigurasi:

- `application.yml` — konfigurasi umum
- `application-dev.yml` — logging verbose, Swagger aktif
- `application-prod.yml` — logging minimal, Swagger dimatikan secara default

---

## 4. Database

Skema saat ini:

```
companies        tenant root
users            akun login (company_id NULL hanya untuk SUPER_ADMIN,
                 employee_id mengaitkan akun ke data karyawan)
refresh_tokens   refresh token (disimpan sebagai hash SHA-256)
departments      departemen, unik per (company_id, name)
positions        jabatan, unik per (company_id, name)
employees        karyawan, unik per (company_id, employee_code)
shifts           jam kerja, unik per (company_id, name)
employee_shifts  jadwal shift, unik per (employee_id, shift_date)
attendances      absensi harian, unik per (employee_id, attendance_date)
company_settings pengaturan per tenant (saat ini: geofence on/off)
locations        lokasi kantor + radius, unik per (company_id, name)
leave_requests   pengajuan cuti/sakit/izin beserta hasil review
attendance_corrections  pengajuan koreksi absensi beserta jejak audit
audit_logs       jejak aktivitas penting (append-only)
plans            katalog paket langganan (level platform, tanpa company_id)
subscriptions    langganan berjalan, satu baris per perusahaan
invoices         tagihan per periode langganan
```

Relasi:

```
companies 1─┬─N users ────────1─1 employees
            ├─N departments ──1─N employees
            ├─N positions ────1─N employees
            ├─N employees ──┬─1─N employee_shifts ─N─1 shifts
            │               └─1─N attendances ─────N─1 shifts
            ├─N shifts
            ├─N employee_shifts
            ├─N attendances
            ├─1 company_settings
            ├─N locations
            ├─N leave_requests ────────N─1 employees
            ├─N attendance_corrections N─1 employees, N─1 attendances
            ├─N audit_logs (company_id & user_id sebagai kolom biasa,
            │               bukan relasi, agar log tetap utuh
            │               walau data yang dirujuk dihapus)
            ├─1 subscriptions ─N─1 plans
            └─N invoices ─────N─1 subscriptions

plans ─── level platform, tidak dimiliki tenant mana pun
```

Uniqueness selalu **per tenant**: dua perusahaan berbeda boleh sama-sama punya
departemen `Produksi` atau karyawan berkode `EMP-001`.

Buat database dan user secara manual (jika tidak memakai Docker):

```sql
CREATE DATABASE attendance_saas
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER 'attendance'@'%' IDENTIFIED BY 'password-anda';
GRANT ALL PRIVILEGES ON attendance_saas.* TO 'attendance'@'%';
FLUSH PRIVILEGES;
```

---

## 5. Migration

Migration dikelola Flyway dan dijalankan otomatis saat aplikasi start.
Script berada di `src/main/resources/db/migration`:

| Versi | File | Isi |
|---|---|---|
| V1 | `V1__init_core_schema.sql` | Tabel `companies`, `users`, `refresh_tokens` |
| V2 | `V2__seed_super_admin.sql` | Seed akun super admin |
| V3 | `V3__phase2_employee_schema.sql` | Tabel `departments`, `positions`, `employees`, kolom `users.employee_id` |
| V4 | `V4__phase3_attendance_schema.sql` | Tabel `shifts`, `employee_shifts`, `attendances` |
| V5 | `V5__phase4_geofence_leave_correction.sql` | Tabel `company_settings`, `locations`, `leave_requests`, `attendance_corrections` |
| V6 | `V6__phase5_audit_and_leave_quota.sql` | Tabel `audit_logs`, kolom `company_settings.annual_leave_quota_days` |
| V7 | `V7__phase6_subscription_and_billing.sql` | Tabel `plans`, `subscriptions`, `invoices`, seed 4 plan, backfill langganan |

Aturan: **jangan pernah mengubah script yang sudah dirilis** — tambahkan versi
baru (`V3__...`). Hibernate berjalan dengan `ddl-auto: validate`, sehingga
aplikasi menolak start bila skema database tidak cocok dengan entity.

Akun bawaan hasil seed:

```
Email    : superadmin@attendance.local
Password : SuperAdmin123!
Role     : SUPER_ADMIN
```

> **Ganti password ini segera setelah login pertama** melalui
> `POST /api/v1/auth/change-password`.

---

## 6. Running project

### 6.1 Lokal (Maven)

**Cara cepat — satu perintah:**

```bash
./run-local.sh              # profil dev, port 8080
./run-local.sh prod         # profil prod
SERVER_PORT=9090 ./run-local.sh
```

Skrip itu mencari JDK 21 dan Maven sendiri (termasuk yang dipasang user-local di
`~/.local/share/dev-tools`, untuk mesin tanpa akses root), menimpa `DB_HOST` ke
loopback, mengisi `JWT_SECRET` pengembangan yang stabil, lalu memeriksa MySQL
hidup dan port 8080 kosong — keduanya gagal lebih awal dengan pesan yang jelas
alih-alih stack trace Hikari atau `Address already in use`. Seluruh variabel di
dalamnya dapat ditimpa lewat environment.

Bagian di bawah menjelaskan langkah manualnya, bila Anda ingin tahu isi skripnya
atau perlu menyimpang darinya.

Dua hal yang paling sering menggagalkan langkah ini:

1. **Jalankan dari dalam folder proyek** (yang berisi `pom.xml`). Dari folder
   induk, Maven gagal dengan `No plugin found for prefix 'spring-boot'` —
   pesannya menyesatkan, penyebabnya hanya salah direktori.
2. **Pastikan Maven memakai JDK 21.** Maven yang memakai JDK 17 gagal dengan
   `release version 21 not supported`. Periksa dengan `mvn -version` — baris
   *Java version* harus 21.

```bash
cd attendance-saas          # wajib: di sinilah pom.xml berada

# Arahkan ke JDK 21 bila JDK default sistem masih 17
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
mvn -version                # pastikan "Java version: 21..."

export DB_HOST=localhost DB_PORT=3306 DB_NAME=attendance_saas
export DB_USERNAME=attendance DB_PASSWORD=password-anda
export JWT_SECRET="$(openssl rand -base64 64 | tr -d '\n')"

mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Aplikasi berjalan di `http://localhost:8080`.

Belum punya JDK 21:

```bash
sudo apt install -y openjdk-21-jdk
sudo update-alternatives --config java    # pilih yang versi 21
```

Tanpa akses root, pasang user-local dan arahkan `JAVA_HOME` ke sana — ini yang
dilakukan `run-local.sh` secara otomatis:

```bash
mkdir -p ~/.local/share/dev-tools && cd ~/.local/share/dev-tools
# Unduh Temurin JDK 21 dan Apache Maven, lalu ekstrak di sini
export JAVA_HOME=~/.local/share/dev-tools/jdk-21.0.12.1+1
export PATH="$JAVA_HOME/bin:~/.local/share/dev-tools/apache-maven-3.9.16/bin:$PATH"
```

Alternatif tanpa Maven, langsung dari jar yang sudah di-build:

```bash
mvn clean package -DskipTests
DB_HOST=localhost SPRING_PROFILES_ACTIVE=dev java -jar target/attendance-saas.jar
```

> Bila `.env` Anda berisi `DB_HOST=mysql`, itu **benar untuk Docker** (nama
> service di `docker-compose.yml`). Untuk menjalankan lokal, timpa dengan
> `DB_HOST=localhost` seperti di atas; jangan ubah isi `.env`.

### 6.2 Docker Compose

```bash
cp .env.example .env    # isi DB_PASSWORD, MYSQL_ROOT_PASSWORD, JWT_SECRET
docker compose up -d
docker compose logs -f backend
```

Dengan Nginx sebagai reverse proxy:

```bash
docker compose --profile with-nginx up -d
```

Hentikan (data MySQL tetap tersimpan di volume):

```bash
docker compose down
```

Hentikan sekaligus hapus data:

```bash
docker compose down -v
```

### 6.3 Test

```bash
mvn test
```

Test memakai H2 in-memory (MySQL mode) sehingga tidak butuh MySQL yang berjalan.
`FlywayMigrationIT` menjalankan seluruh migration lalu memvalidasinya terhadap
mapping JPA, sehingga skema dan entity tidak dapat menyimpang tanpa ketahuan.

**Status verifikasi di MySQL 8 asli.** Rantai V1–V7 sudah dijalankan pada MySQL
8.0.46 sungguhan (5 September 2026): ketujuh migration lolos, `ddl-auto:
validate` cocok, seed super admin dan katalog plan terisi, 30 foreign key
terpasang, seluruh tabel `utf8mb4_unicode_ci` / InnoDB, dan menjalankan ulang
aplikasi menghasilkan *"No migration necessary"*. Alur login, registrasi
perusahaan, shift malam, check-in, cuti, dashboard, dan batas plan juga diuji
lewat HTTP pada database tersebut.

---

## 7. Swagger

| URL | Isi |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/v3/api-docs` | Dokumen OpenAPI (JSON) |

Untuk mencoba endpoint yang terproteksi: klik **Authorize**, lalu tempel
*access token* (tanpa prefix `Bearer`).

Area yang terdokumentasi:

```
Authentication   Company      Department   Position
Employee         Shift        Employee Shift
Attendance       Attendance Correction     Leave
Location         Dashboard    Report       Audit Log
Plan             Subscription Invoice      System Admin
```

Pada profil `prod` Swagger dimatikan kecuali `SWAGGER_ENABLED=true`.

---

## 8. Authentication

Alur token:

```
POST /api/v1/auth/login
   -> accessToken  (JWT, umur pendek, dikirim tiap request)
   -> refreshToken (opaque, umur panjang, disimpan hash-nya di database)

POST /api/v1/auth/refresh
   -> pasangan token baru; refresh token lama langsung dicabut (rotasi)
```

Kirim access token pada setiap request terproteksi:

```
Authorization: Bearer <accessToken>
```

Isi access token (claim):

```
sub        email user
uid        user id
companyId  tenant user (-1 untuk SUPER_ADMIN)
role       SUPER_ADMIN | COMPANY_ADMIN | HR | SUPERVISOR | EMPLOYEE
```

Catatan keamanan:

- Password di-hash BCrypt (cost 12); tidak pernah dikembalikan lewat API.
- Refresh token disimpan sebagai hash SHA-256 dan dirotasi setiap dipakai.
  Pemakaian ulang token yang sudah dirotasi dianggap pencurian: seluruh token
  milik user tersebut langsung dicabut.
- Mengubah password mencabut seluruh sesi yang sedang berjalan.
- `company_id` **selalu** diambil dari JWT, tidak pernah dari body atau query
  parameter yang dikirim client.
- Email bersifat unik secara global karena login hanya memakai email + password.

### Role

| Role | Cakupan |
|---|---|
| `SUPER_ADMIN` | Lintas tenant: kelola perusahaan, subscription, dashboard sistem |
| `COMPANY_ADMIN` | Satu tenant: karyawan, departemen, posisi, shift, absensi, cuti, setting |
| `HR` | Satu tenant: karyawan, absensi, cuti, laporan |
| `SUPERVISOR` | Absensi tim, approval cuti dan koreksi absensi |
| `EMPLOYEE` | Check-in/out, riwayat absensi, pengajuan cuti/koreksi, profil |

---

## 9. API examples

Base URL: `http://localhost:8080/api/v1`

Semua response memakai amplop yang sama:

```json
{ "success": true,  "message": "...", "data": { }, "timestamp": "..." }
{ "success": false, "message": "...", "errorCode": "EMPLOYEE_NOT_FOUND", "errors": [] }
```

### 9.1 Registrasi perusahaan (publik)

```bash
curl -X POST http://localhost:8080/api/v1/auth/register-company \
  -H 'Content-Type: application/json' \
  -d '{
    "companyName": "PT Sumber Makmur",
    "companyCode": "sumber-makmur",
    "companyEmail": "info@sumbermakmur.co.id",
    "phone": "+628123456789",
    "address": "Jl. Merdeka No. 1, Jakarta",
    "timezone": "Asia/Jakarta",
    "adminName": "Budi Santoso",
    "adminEmail": "budi@sumbermakmur.co.id",
    "adminPassword": "Password123!"
  }'
```

`201 Created`:

```json
{
  "success": true,
  "message": "Perusahaan berhasil didaftarkan",
  "data": {
    "company": {
      "id": 1,
      "name": "PT Sumber Makmur",
      "code": "sumber-makmur",
      "email": "info@sumbermakmur.co.id",
      "timezone": "Asia/Jakarta",
      "status": "ACTIVE"
    },
    "adminUserId": 2,
    "adminEmail": "budi@sumbermakmur.co.id"
  }
}
```

### 9.2 Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"budi@sumbermakmur.co.id","password":"Password123!"}'
```

```json
{
  "success": true,
  "message": "Login berhasil",
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "b3RoZXItcmFuZG9tLXRva2Vu...",
    "tokenType": "Bearer",
    "expiresInSeconds": 3600,
    "user": {
      "id": 2,
      "name": "Budi Santoso",
      "email": "budi@sumbermakmur.co.id",
      "role": "COMPANY_ADMIN",
      "companyId": 1,
      "companyName": "PT Sumber Makmur",
      "companyCode": "sumber-makmur",
      "timezone": "Asia/Jakarta"
    }
  }
}
```

### 9.3 Profil user yang sedang login

```bash
curl http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

### 9.4 Profil perusahaan sendiri

```bash
curl http://localhost:8080/api/v1/companies/me \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

### 9.5 Refresh token

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
```

### 9.6 Ganti password

```bash
curl -X POST http://localhost:8080/api/v1/auth/change-password \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"currentPassword":"Password123!","newPassword":"PasswordBaru1"}'
```

### 9.7 Daftar seluruh perusahaan (SUPER_ADMIN)

```bash
curl "http://localhost:8080/api/v1/companies?page=0&size=20&keyword=makmur" \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN"
```

```json
{
  "success": true,
  "data": {
    "data": [ { "id": 1, "name": "PT Sumber Makmur", "code": "sumber-makmur" } ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

### 9.8 Master data: departemen, posisi, karyawan

Tambah departemen:

```bash
curl -X POST http://localhost:8080/api/v1/departments \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Produksi","description":"Lini produksi utama"}'
```

Tambah karyawan:

```bash
curl -X POST http://localhost:8080/api/v1/employees \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "employeeCode": "EMP-001",
    "name": "Siti Rahayu",
    "email": "siti@sumbermakmur.co.id",
    "phone": "+628111222333",
    "departmentId": 1,
    "positionId": 1,
    "joinDate": "2026-01-15"
  }'
```

Cari karyawan dengan filter dan pagination:

```bash
curl "http://localhost:8080/api/v1/employees?keyword=siti&departmentId=1&status=ACTIVE&page=0&size=20&sort=name,asc" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Parameter yang didukung:

| Parameter | Keterangan |
|---|---|
| `keyword` | Cocokkan pada kode karyawan, nama, email, atau telepon |
| `departmentId` | Filter satu departemen |
| `positionId` | Filter satu posisi |
| `status` | `ACTIVE`, `INACTIVE`, atau `RESIGNED` |
| `page`, `size` | Pagination, `page` mulai dari 0 |
| `sort` | Contoh `name,asc` atau `employeeCode,desc` |

Buatkan akun login untuk karyawan (dibutuhkan sebelum check-in di Phase 3):

```bash
curl -X POST http://localhost:8080/api/v1/employees/1/account \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"email":"siti@sumbermakmur.co.id","password":"Password123!","role":"EMPLOYEE"}'
```

Role yang boleh diberikan lewat endpoint ini hanya `EMPLOYEE`, `SUPERVISOR`,
dan `HR` — `COMPANY_ADMIN` dan `SUPER_ADMIN` ditolak dengan
`ROLE_NOT_GRANTABLE`.

`DELETE /api/v1/employees/{id}` bersifat **soft delete**: status karyawan
menjadi `RESIGNED` dan barisnya tetap ada, supaya riwayat absensi tidak
kehilangan pemiliknya. Departemen dan posisi sebaliknya dihapus permanen, tapi
ditolak dengan `409` selama masih dipakai karyawan.

### 9.9 Shift dan absensi

#### Membuat shift

```bash
curl -X POST http://localhost:8080/api/v1/shifts \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Shift 3 Malam",
    "startTime": "22:00",
    "endTime": "07:00",
    "lateToleranceMinutes": 15,
    "earlyLeaveToleranceMinutes": 10,
    "defaultShift": false
  }'
```

`endTime` yang lebih awal dari `startTime` berarti **shift melewati tengah
malam**; response menandainya dengan `"crossesMidnight": true`.

Satu perusahaan boleh punya paling banyak satu shift dengan `defaultShift:
true`. Shift itu dipakai ketika karyawan tidak punya jadwal khusus pada tanggal
tersebut; menandai shift lain sebagai default otomatis mencabut flag yang lama.

#### Menjadwalkan shift

```bash
curl -X POST http://localhost:8080/api/v1/employee-shifts \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "employeeId": 1,
    "shiftId": 3,
    "startDate": "2026-09-07",
    "endDate": "2026-09-13",
    "overwriteExisting": false
  }'
```

Satu entri dibuat untuk tiap tanggal dalam rentang (maksimal 366 hari). Jika
sudah ada jadwal pada rentang tersebut, request ditolak `409` kecuali
`overwriteExisting: true` — supaya penjadwalan massal tidak diam-diam menimpa
roster yang sudah disusun manual.

#### Check-in

```bash
curl -X POST http://localhost:8080/api/v1/attendance/check-in \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "latitude": -6.200000,
    "longitude": 106.816666,
    "photo": "https://storage.example.com/checkin/emp-001.jpg"
  }'
```

```json
{
  "success": true,
  "message": "Check-in berhasil",
  "data": {
    "id": 1,
    "employeeCode": "EMP-001",
    "shiftName": "Shift 3 Malam",
    "shiftStartTime": "22:00",
    "shiftEndTime": "07:00",
    "attendanceDate": "2026-09-05",
    "checkIn": "2026-09-05T15:00:00Z",
    "checkInLocal": "22:00:00",
    "timezone": "Asia/Jakarta",
    "status": "PRESENT",
    "lateMinutes": 0,
    "earlyLeaveMinutes": 0,
    "workMinutes": 0
  }
}
```

Shift dan `attendanceDate` **ditentukan server**, bukan dikirim client.

#### Check-out

```bash
curl -X POST http://localhost:8080/api/v1/attendance/check-out \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"latitude":-6.200000,"longitude":106.816666}'
```

#### Riwayat dan filter

```bash
# Riwayat sendiri
curl "http://localhost:8080/api/v1/attendance/me?startDate=2026-09-01&endDate=2026-09-30" \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN"

# Seluruh karyawan, difilter
curl "http://localhost:8080/api/v1/attendance?departmentId=1&status=LATE&startDate=2026-09-01&endDate=2026-09-30&page=0&size=20" \
  -H "Authorization: Bearer $HR_TOKEN"
```

Filter yang didukung: `employeeId`, `departmentId`, `shiftId`, `status`,
`startDate`, `endDate`, plus `page`, `size`, `sort`.

### 9.10 Model waktu dan aturan absensi

Ini bagian yang paling mudah salah, jadi dicatat eksplisit.

**Timezone.** Semua logika absensi memakai `companies.timezone`, bukan timezone
server. `check_in` dan `check_out` disimpan sebagai instant UTC; response juga
menyertakan `checkInLocal` / `checkOutLocal` dalam jam lokal perusahaan supaya
client tidak perlu mengonversi sendiri.

**Shift malam.** `attendance_date` adalah **tanggal shift dimulai**, bukan
tanggal kalender saat tombol ditekan. Untuk shift 22:00 → 07:00:

```
Selasa 21:55  check-in   -> attendance_date = Selasa
Rabu   00:30  check-in   -> attendance_date = Selasa  (masih shift yang sama)
Rabu   07:00  check-out  -> attendance_date = Selasa  (satu baris, bukan dua)
```

Server memilih shift dengan memeriksa jadwal hari ini **dan** kemarin, lalu
mengambil jadwal yang jendelanya benar-benar memuat waktu check-in. Check-in
diterima paling awal 6 jam sebelum shift mulai.

**Terlambat.** Toleransi adalah masa tenggang, bukan potongan:

```
actual_check_in > shift_start + toleransi  ->  terlambat
late_minutes = selisih penuh dari shift_start
```

Datang 08:20 dengan toleransi 15 menit berarti terlambat **20 menit**, bukan 5.
Datang 08:15 tepat masih dianggap tidak terlambat.

**Pulang cepat.** Simetris:

```
actual_check_out < shift_end - toleransi  ->  pulang cepat
early_leave_minutes = selisih penuh dari shift_end
```

**work_minutes** adalah selisih check-in dan check-out; istirahat belum
dipotong.

**Status.** `PRESENT` bila `late_minutes = 0`, `LATE` bila lebih. Status
`ABSENT`, `LEAVE`, `SICK`, `PERMIT`, dan `HOLIDAY` sudah ada di enum tetapi
baru diisi pada Phase 4–5.

**Aturan yang ditegakkan:**

| Aturan | Error code |
|---|---|
| Tidak boleh check-in dua kali pada shift yang sama | `ALREADY_CHECKED_IN` |
| Tidak boleh check-out tanpa check-in | `NOT_CHECKED_IN` |
| Tidak boleh check-out dua kali | `ALREADY_CHECKED_OUT` |
| Harus punya jadwal atau shift default | `NO_SHIFT_ASSIGNED` |
| Karyawan nonaktif tidak dapat absen | `EMPLOYEE_INACTIVE` |
| Akun tanpa data karyawan tidak dapat absen | `NO_EMPLOYEE_PROFILE` |

### 9.11 Geofencing

Aktifkan validasi lokasi hanya setelah lokasi kantor didefinisikan — geofence
menyala tanpa kantor akan menolak semua check-in dengan `NO_ACTIVE_LOCATION`.

```bash
# 1. Definisikan kantor
curl -X POST http://localhost:8080/api/v1/locations \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Kantor Pusat Jakarta",
    "address": "Jl. Merdeka No. 1",
    "latitude": -6.1753924,
    "longitude": 106.8271528,
    "radiusMeter": 150
  }'

# 2. Baru nyalakan geofence
curl -X PUT http://localhost:8080/api/v1/locations/settings \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"geofenceEnabled": true}'
```

Cara kerjanya saat check-in:

```
geofenceEnabled = false  ->  koordinat tetap disimpan, tidak pernah ditolak
geofenceEnabled = true   ->  hitung jarak ke setiap kantor aktif
                             jarak <= radius kantor terdekat  ->  diterima
                             selain itu                        ->  OUTSIDE_GEOFENCE
```

Jarak dihitung dengan formula haversine. Cukup berada dalam radius **salah
satu** kantor aktif. Check-in yang ditolak tidak menyisakan baris absensi.

Default `geofenceEnabled` adalah `false`, termasuk untuk perusahaan yang sudah
ada saat fitur ini dipasang — menyalakannya selalu keputusan eksplisit.

**Catatan cakupan:** validasi lokasi diterapkan pada **check-in saja**, sesuai
spesifikasi. Koordinat check-out tetap direkam tetapi tidak ditolak. Kalau Anda
ingin check-out ikut divalidasi, tinggal panggil
`geofenceService.assertWithinGeofence(...)` di `AttendanceService.checkOut`.

### 9.12 Cuti dan izin

```bash
# Karyawan mengajukan
curl -X POST http://localhost:8080/api/v1/leave \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "leaveType": "CUTI",
    "startDate": "2026-09-10",
    "endDate": "2026-09-12",
    "reason": "Acara keluarga di luar kota"
  }'

# Supervisor / HR menyetujui
curl -X PUT http://localhost:8080/api/v1/leave/1/approve \
  -H "Authorization: Bearer $SUPERVISOR_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"note":"Disetujui, pekerjaan sudah didelegasikan"}'

# Menolak — alasan wajib diisi
curl -X PUT http://localhost:8080/api/v1/leave/1/reject \
  -H "Authorization: Bearer $SUPERVISOR_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"note":"Beban kerja tim sedang tinggi"}'
```

Jenis dan status:

```
leaveType : CUTI | SAKIT | IZIN
status    : PENDING -> APPROVED | REJECTED
                    -> CANCELLED (dibatalkan sendiri selagi PENDING)
```

Aturan yang ditegakkan:

- Pengajuan yang **beririsan** dengan pengajuan `PENDING` atau `APPROVED`
  ditolak `LEAVE_ALREADY_EXISTS`. Yang sudah `CANCELLED` atau `REJECTED` tidak
  memblokir.
- **Tidak seorang pun menyetujui pengajuannya sendiri**, termasuk supervisor
  yang punya data karyawan (`CANNOT_REVIEW_OWN_REQUEST`).
- Penolakan wajib menyertakan alasan.
- Pengajuan yang sudah diputuskan tidak dapat diputuskan ulang
  (`LEAVE_NOT_PENDING`).

**Persetujuan langsung menandai absensi.** Inilah cara aturan §24 ("karyawan
dengan cuti APPROVED tidak dianggap ABSENT") benar-benar dijalankan, bukan
sekadar dinyatakan:

```
CUTI  disetujui -> baris attendance status LEAVE
SAKIT disetujui -> baris attendance status SICK
IZIN  disetujui -> baris attendance status PERMIT
```

Satu baris dibuat untuk setiap tanggal dalam rentang. Hari yang **sudah punya
check-in tidak ditimpa** — karyawan yang tetap masuk kerja tercatat sebagai
bekerja, bukan cuti.

### 9.13 Koreksi absensi

```bash
# Karyawan mengajukan koreksi
curl -X POST http://localhost:8080/api/v1/attendance-corrections \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "attendanceDate": "2026-09-05",
    "checkIn": "08:00",
    "checkOut": "17:00",
    "reason": "Lupa melakukan check-in"
  }'

# HR / Supervisor menyetujui dan koreksi langsung diterapkan
curl -X PUT http://localhost:8080/api/v1/attendance-corrections/1/approve \
  -H "Authorization: Bearer $HR_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"note":"Terverifikasi dengan rekaman CCTV"}'
```

Saat disetujui, server:

1. Membuat baris absensi bila hari itu memang belum ada.
2. Menyimpan nilai lama ke `previousCheckIn` / `previousCheckOut` — **jejak
   audit**, sehingga perubahan selalu bisa direkonstruksi.
3. Menghitung ulang `lateMinutes`, `earlyLeaveMinutes`, dan `workMinutes`
   terhadap shift tanggal tersebut.
4. Mencatat siapa yang menyetujui dan kapan.

Jam yang dikirim adalah **jam lokal perusahaan**. Untuk shift malam, check-out
yang lebih awal dari check-in otomatis dianggap hari berikutnya (`22:00` →
`07:00` menghasilkan 540 menit kerja, bukan negatif).

Aturan: minimal salah satu dari `checkIn`/`checkOut` harus diisi, tanggal masa
depan ditolak, hanya boleh ada satu koreksi `PENDING` per tanggal, dan tidak
ada yang menyetujui koreksinya sendiri.

### 9.14 Dashboard

```bash
# Dashboard perusahaan (COMPANY_ADMIN / HR / SUPERVISOR)
curl "http://localhost:8080/api/v1/dashboard" \
  -H "Authorization: Bearer $HR_TOKEN"
```

```json
{
  "success": true,
  "data": {
    "date": "2026-09-07",
    "timezone": "Asia/Jakarta",
    "totalEmployees": 120,
    "expectedToday": 118,
    "present": 100,
    "late": 10,
    "absent": 3,
    "leave": 5,
    "pendingLeaveRequests": 4,
    "pendingCorrections": 2
  }
}
```

**Bagaimana `absent` dihitung.** Tidak ada proses yang menulis baris berstatus
`ABSENT`, jadi angka ini diturunkan saat query:

```
expectedToday = karyawan yang terjadwal hari itu
                (atau seluruh karyawan aktif bila perusahaan punya shift default)
absent        = expectedToday - jumlah karyawan yang punya catatan absensi hari itu
```

Pembaginya adalah **jadwal**, bukan jumlah karyawan. Karyawan yang memang libur
hari itu tidak ikut terhitung mangkir. Karyawan berstatus `RESIGNED` tidak
masuk `totalEmployees` maupun `expectedToday`.

```bash
# Dashboard karyawan sendiri
curl "http://localhost:8080/api/v1/dashboard/me" \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN"
```

```json
{
  "success": true,
  "data": {
    "employeeCode": "EMP-001",
    "date": "2026-09-07",
    "timezone": "Asia/Jakarta",
    "todayAttendance": { "status": "PRESENT", "checkInLocal": "07:55:00" },
    "currentShift": { "name": "Shift Pagi", "startTime": "08:00", "endTime": "17:00" },
    "thisMonth": {
      "monthStart": "2026-09-01",
      "monthEnd": "2026-09-30",
      "presentDays": 5,
      "lateDays": 1,
      "leaveDays": 0,
      "absentDays": 0,
      "totalLateMinutes": 20,
      "totalWorkMinutes": 2680
    },
    "leaveBalance": {
      "year": 2026,
      "quotaDays": 12,
      "usedDays": 3,
      "remainingDays": 9,
      "pendingDays": 0
    }
  }
}
```

`leaveBalance` hanya menghitung **CUTI**; sakit dan izin tidak memotong kuota
tahunan. Kuotanya berasal dari `company_settings.annual_leave_quota_days`
(default 12 hari, minimum menurut UU Ketenagakerjaan). Cuti yang masih `PENDING`
belum memotong sisa, tapi ditampilkan sebagai `pendingDays`.

### 9.15 Laporan

```bash
# Harian: satu tanggal, satu baris per karyawan
curl "http://localhost:8080/api/v1/reports/attendance/daily?date=2026-09-07&departmentId=1&status=LATE" \
  -H "Authorization: Bearer $HR_TOKEN"

# Bulanan: rekap per karyawan
curl "http://localhost:8080/api/v1/reports/attendance/monthly?year=2026&month=9&departmentId=1" \
  -H "Authorization: Bearer $HR_TOKEN"

# Per karyawan pada rentang tanggal
curl "http://localhost:8080/api/v1/reports/attendance/employee/1?startDate=2026-09-01&endDate=2026-09-30" \
  -H "Authorization: Bearer $HR_TOKEN"
```

Filter yang didukung:

| Laporan | Parameter |
|---|---|
| daily | `date`, `departmentId`, `shiftId`, `status` |
| monthly | `year`, `month`, `departmentId`, `employeeId` |
| employee | `startDate`, `endDate` (default: bulan berjalan) |

Rekap bulanan berisi `recordedDays`, `presentDays`, `lateDays`, `leaveDays`,
`absentDays`, `totalLateMinutes`, `totalEarlyLeaveMinutes`, dan
`totalWorkMinutes` per karyawan. Agregasi dilakukan di database, bukan dengan
menarik seluruh baris absensi ke memori. Rentang laporan dibatasi 366 hari.

### 9.16 Audit log

```bash
curl "http://localhost:8080/api/v1/audit-logs?action=APPROVE&startDate=2026-09-01&page=0&size=50" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Aksi yang dicatat:

```
LOGIN         LOGIN_FAILED   LOGOUT
CREATE        UPDATE         DELETE
CHECK_IN      CHECK_OUT
APPROVE       REJECT
```

Setiap baris menyimpan `company_id`, `user_id`, `action`, `entity`,
`entity_id`, `description`, `ip_address`, dan `created_at`. Alamat IP diambil
dari `X-Forwarded-For` / `X-Real-IP` bila ada (header yang diteruskan Nginx
bawaan), selain itu dari alamat koneksi langsung.

Catatan desain:

- **Append-only.** Tidak ada endpoint untuk mengubah atau menghapus log, dan
  entity-nya tidak punya `updated_at`.
- **`company_id` dan `user_id` adalah kolom biasa, bukan relasi**, supaya
  sebuah log tetap utuh walau data yang dirujuknya dihapus.
- **Penulisan log tidak pernah menggagalkan operasi bisnis.** Tiap entri
  ditulis di transaksi terpisah (`REQUIRES_NEW`) dan kegagalannya hanya
  di-log sebagai WARN. Konsekuensinya, sebuah entri bisa saja tercatat untuk
  operasi yang transaksinya kemudian di-rollback — arah kompromi yang lebih
  aman untuk sebuah log.
- **Password dan token tidak pernah masuk ke `description`.**

### 9.17 Subscription, batas plan, dan billing

#### Katalog plan bawaan

| Kode | Karyawan | Lokasi | Pengguna | Geofence | Harga / bulan |
|---|---|---|---|---|---|
| `FREE` | 10 | 1 | 15 | — | Rp 0 |
| `BASIC` | 50 | 3 | 75 | ya | Rp 299.000 |
| `PRO` | 250 | 10 | 400 | ya | Rp 999.000 |
| `ENTERPRISE` | tanpa batas | tanpa batas | tanpa batas | ya | Rp 4.999.000 |

Batas yang dikosongkan (`null`) berarti **tanpa batas**. Registrasi mandiri
selalu mendarat di `FREE`.

```bash
# Super admin menambah plan
curl -X POST http://localhost:8080/api/v1/plans \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "code": "STARTER",
    "name": "Starter",
    "priceAmount": 149000.00,
    "currency": "IDR",
    "billingPeriod": "MONTHLY",
    "maxEmployees": 25,
    "maxLocations": 2,
    "maxUsers": 30,
    "geofenceIncluded": true
  }'
```

Plan yang masih dipakai perusahaan **tidak dapat dihapus** (`PLAN_IN_USE`) —
menghapusnya akan membuat tenant tanpa langganan. Pensiunkan dengan
`active: false` supaya tidak lagi bisa dipilih tanpa mengganggu pemakainya.

#### Batas plan

```bash
# Perusahaan melihat kuota dan pemakaiannya
curl http://localhost:8080/api/v1/subscriptions/me \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

```json
{
  "success": true,
  "data": {
    "subscription": { "plan": { "code": "BASIC" }, "status": "ACTIVE" },
    "employees": { "used": 42, "limit": 50, "remaining": 8, "unlimited": false },
    "locations": { "used": 2,  "limit": 3,  "remaining": 1, "unlimited": false },
    "users":     { "used": 45, "limit": 75, "remaining": 30, "unlimited": false },
    "geofenceAvailable": true
  }
}
```

Batas diperiksa **sebelum apa pun ditulis**, jadi request yang ditolak tidak
meninggalkan jejak. Yang ditegakkan:

| Batas | Dicek saat | Error code |
|---|---|---|
| `maxEmployees` | Menambah karyawan | `EMPLOYEE_LIMIT_REACHED` |
| `maxLocations` | Menambah lokasi kantor | `LOCATION_LIMIT_REACHED` |
| `maxUsers` | Membuat akun karyawan | `USER_LIMIT_REACHED` |
| `geofenceIncluded` | Menyalakan geofence | `FEATURE_NOT_IN_PLAN` |
| Status langganan | Semua penambahan di atas | `SUBSCRIPTION_NOT_ACTIVE` |

Semuanya mengembalikan **402 Payment Required** — status yang tepat untuk
"permintaan sah, tapi paketnya tidak mencukupi", dan mudah dibedakan klien dari
403 (hak akses kurang).

**Turun plan tidak pernah menghapus data.** Perusahaan dengan 40 karyawan yang
pindah ke plan berkuota 10 tetap memegang 40 karyawannya; batas hanya
menghentikan penambahan berikutnya. Aturan penagihan tidak pantas menghapus data
operasional orang.

Begitu pula geofencing: tenant yang turun ke plan tanpa fitur itu berhenti
mendapat penolakan check-in, bukan kehilangan akses absensi.

#### Siklus langganan

```
                    assignPlan (trialDays > 0)
                              │
                              ▼
   assignPlan ──────────► TRIAL ──── periode habis ────► ACTIVE
        │                                                  │  ▲
        └──────────────► ACTIVE ◄──────── bayar ────────────┘  │
                           │                                   │
                    tagihan jatuh tempo                        │
                           ▼                                   │
                        PAST_DUE ─────────── bayar ────────────┘
                           │
                    cancel │                    cancel
                           ▼                        │
                       CANCELLED ◄──────────────────┘
                           │
                    periode habis
                           ▼
                        EXPIRED ──── reactivate ────► ACTIVE
```

`TRIAL` dan `ACTIVE` boleh menambah data; `PAST_DUE`, `CANCELLED`, dan
`EXPIRED` tetap dapat membaca datanya tetapi tidak dapat menambah.

**Membatalkan langganan tidak langsung memutus layanan.** Statusnya menjadi
`CANCELLED`, perpanjangan otomatis dimatikan, dan perusahaan tetap dapat memakai
layanan sampai periode yang sudah dibayar berakhir — baru kemudian menjadi
`EXPIRED`.

```bash
# Super admin memindahkan perusahaan ke plan lain, opsional dengan masa trial
curl -X PUT http://localhost:8080/api/v1/subscriptions/company/1/plan \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"planId": 3, "trialDays": 14, "autoRenew": true}'

curl -X PUT http://localhost:8080/api/v1/subscriptions/company/1/cancel \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN"
```

#### Tagihan

```bash
# Tagihan perusahaan sendiri
curl http://localhost:8080/api/v1/invoices/me \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Menandai lunas (super admin)
curl -X PUT http://localhost:8080/api/v1/invoices/1/pay \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"paymentReference": "TRF-20260907-0012"}'

# Menjalankan siklus penagihan
curl -X POST http://localhost:8080/api/v1/invoices/billing-cycle/run \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN"
```

Siklus penagihan melakukan tiga hal, dan **idempoten** sehingga aman dijalankan
berulang — ditujukan untuk dipanggil scheduler harian:

1. Tagihan yang lewat jatuh tempo (14 hari) ditandai `OVERDUE`, langganannya
   menjadi `PAST_DUE`.
2. Langganan yang periodenya berakhir diperpanjang, lalu tagihan periode
   berikutnya diterbitkan.
3. Langganan tanpa perpanjangan otomatis menjadi `EXPIRED`.

Dua keputusan yang perlu diketahui:

- **Nilai tagihan dibekukan saat terbit.** `plan_code`, `plan_name`, dan
  `amount` disalin ke baris tagihan, bukan dibaca lewat relasi. Menaikkan harga
  plan tidak boleh menulis ulang tagihan yang sudah dikirim.
- **Plan gratis dan masa trial tidak pernah ditagih.** Ditambah unique constraint
  `(subscription_id, period_start)`, satu periode tidak mungkin tertagih dua kali.

#### Batas yang jujur soal billing

Tidak ada integrasi payment gateway. `PUT /invoices/{id}/pay` adalah satu-satunya
titik masuk pelunasan, dan itulah **seam** yang akan dipanggil webhook penyedia
pembayaran bila nanti dipasang — semua yang di hilirnya (mengangkat `PAST_DUE`
kembali ke `ACTIVE`, audit log, laporan pendapatan) sudah berjalan. Yang belum ada
hanyalah bagian yang benar-benar memindahkan uang.

#### Dashboard sistem

```bash
curl http://localhost:8080/api/v1/admin/dashboard \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN"
```

Berisi jumlah tenant (total, aktif, disuspend), total karyawan dan pengguna
seluruh platform, sebaran langganan menurut status dan plan, MRR, jumlah tagihan
belum lunas dan lewat tempo, serta pendapatan bulan berjalan.

MRR dinormalkan ke bulanan — plan tahunan dihitung seperduabelas — supaya plan
dengan periode berbeda dapat dibandingkan. Trial dan langganan yang dibatalkan
tidak dihitung karena belum menjadi pendapatan.

### 9.18 Daftar endpoint

| Method | Endpoint | Akses |
|---|---|---|
| `POST` | `/api/v1/auth/register-company` | Publik |
| `POST` | `/api/v1/auth/login` | Publik |
| `POST` | `/api/v1/auth/refresh` | Publik |
| `POST` | `/api/v1/auth/logout` | Terautentikasi |
| `GET` | `/api/v1/auth/me` | Terautentikasi |
| `POST` | `/api/v1/auth/change-password` | Terautentikasi |
| `GET` | `/api/v1/companies` | `SUPER_ADMIN` |
| `GET` | `/api/v1/companies/me` | Semua role tenant |
| `PUT` | `/api/v1/companies/me` | `COMPANY_ADMIN` |
| `GET` | `/api/v1/companies/{id}` | `SUPER_ADMIN`, `COMPANY_ADMIN` (hanya tenant sendiri) |
| `PUT` | `/api/v1/companies/{id}` | `SUPER_ADMIN`, `COMPANY_ADMIN` (hanya tenant sendiri) |
| `PATCH` | `/api/v1/companies/{id}/status` | `SUPER_ADMIN` |
| `GET` | `/api/v1/departments` | Semua role tenant |
| `GET` | `/api/v1/departments/all` | Semua role tenant |
| `GET` | `/api/v1/departments/{id}` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `POST` | `/api/v1/departments` | `COMPANY_ADMIN`, `HR` |
| `PUT` | `/api/v1/departments/{id}` | `COMPANY_ADMIN`, `HR` |
| `DELETE` | `/api/v1/departments/{id}` | `COMPANY_ADMIN` |
| `GET` | `/api/v1/positions` | Semua role tenant |
| `GET` | `/api/v1/positions/all` | Semua role tenant |
| `GET` | `/api/v1/positions/{id}` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `POST` | `/api/v1/positions` | `COMPANY_ADMIN`, `HR` |
| `PUT` | `/api/v1/positions/{id}` | `COMPANY_ADMIN`, `HR` |
| `DELETE` | `/api/v1/positions/{id}` | `COMPANY_ADMIN` |
| `GET` | `/api/v1/employees` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `GET` | `/api/v1/employees/me` | `HR`, `SUPERVISOR`, `EMPLOYEE` |
| `GET` | `/api/v1/employees/{id}` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `POST` | `/api/v1/employees` | `COMPANY_ADMIN`, `HR` |
| `PUT` | `/api/v1/employees/{id}` | `COMPANY_ADMIN`, `HR` |
| `DELETE` | `/api/v1/employees/{id}` | `COMPANY_ADMIN`, `HR` (soft delete) |
| `POST` | `/api/v1/employees/{id}/account` | `COMPANY_ADMIN`, `HR` |
| `GET` | `/api/v1/shifts` | Semua role tenant |
| `GET` | `/api/v1/shifts/all` | Semua role tenant |
| `GET` | `/api/v1/shifts/{id}` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `POST` | `/api/v1/shifts` | `COMPANY_ADMIN`, `HR` |
| `PUT` | `/api/v1/shifts/{id}` | `COMPANY_ADMIN`, `HR` |
| `DELETE` | `/api/v1/shifts/{id}` | `COMPANY_ADMIN` |
| `POST` | `/api/v1/employee-shifts` | `COMPANY_ADMIN`, `HR` |
| `GET` | `/api/v1/employee-shifts` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `GET` | `/api/v1/employee-shifts/me` | `HR`, `SUPERVISOR`, `EMPLOYEE` |
| `DELETE` | `/api/v1/employee-shifts/{id}` | `COMPANY_ADMIN`, `HR` |
| `POST` | `/api/v1/attendance/check-in` | `HR`, `SUPERVISOR`, `EMPLOYEE` |
| `POST` | `/api/v1/attendance/check-out` | `HR`, `SUPERVISOR`, `EMPLOYEE` |
| `GET` | `/api/v1/attendance/current` | `HR`, `SUPERVISOR`, `EMPLOYEE` |
| `GET` | `/api/v1/attendance/me` | `HR`, `SUPERVISOR`, `EMPLOYEE` |
| `GET` | `/api/v1/attendance` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `GET` | `/api/v1/attendance/{id}` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `GET` | `/api/v1/locations` | Semua role tenant |
| `GET` | `/api/v1/locations/{id}` | `COMPANY_ADMIN`, `HR` |
| `POST` | `/api/v1/locations` | `COMPANY_ADMIN` |
| `PUT` | `/api/v1/locations/{id}` | `COMPANY_ADMIN` |
| `DELETE` | `/api/v1/locations/{id}` | `COMPANY_ADMIN` |
| `GET` | `/api/v1/locations/settings` | `COMPANY_ADMIN`, `HR` |
| `PUT` | `/api/v1/locations/settings` | `COMPANY_ADMIN` |
| `POST` | `/api/v1/leave` | `HR`, `SUPERVISOR`, `EMPLOYEE` |
| `GET` | `/api/v1/leave` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `GET` | `/api/v1/leave/me` | `HR`, `SUPERVISOR`, `EMPLOYEE` |
| `GET` | `/api/v1/leave/{id}` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `PUT` | `/api/v1/leave/{id}/approve` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `PUT` | `/api/v1/leave/{id}/reject` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `PUT` | `/api/v1/leave/{id}/cancel` | Pemilik pengajuan |
| `POST` | `/api/v1/attendance-corrections` | `HR`, `SUPERVISOR`, `EMPLOYEE` |
| `GET` | `/api/v1/attendance-corrections` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `GET` | `/api/v1/attendance-corrections/me` | `HR`, `SUPERVISOR`, `EMPLOYEE` |
| `GET` | `/api/v1/attendance-corrections/{id}` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `PUT` | `/api/v1/attendance-corrections/{id}/approve` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `PUT` | `/api/v1/attendance-corrections/{id}/reject` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `GET` | `/api/v1/dashboard` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `GET` | `/api/v1/dashboard/me` | `HR`, `SUPERVISOR`, `EMPLOYEE` |
| `GET` | `/api/v1/reports/attendance/daily` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `GET` | `/api/v1/reports/attendance/monthly` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `GET` | `/api/v1/reports/attendance/employee/{id}` | `COMPANY_ADMIN`, `HR`, `SUPERVISOR` |
| `GET` | `/api/v1/audit-logs` | `COMPANY_ADMIN`, `HR` |
| `GET` | `/api/v1/plans` | `SUPER_ADMIN` |
| `GET` | `/api/v1/plans/available` | `SUPER_ADMIN`, `COMPANY_ADMIN` |
| `GET` | `/api/v1/plans/{id}` | `SUPER_ADMIN` |
| `POST` | `/api/v1/plans` | `SUPER_ADMIN` |
| `PUT` | `/api/v1/plans/{id}` | `SUPER_ADMIN` |
| `DELETE` | `/api/v1/plans/{id}` | `SUPER_ADMIN` |
| `GET` | `/api/v1/subscriptions` | `SUPER_ADMIN` |
| `GET` | `/api/v1/subscriptions/me` | `COMPANY_ADMIN`, `HR` |
| `GET` | `/api/v1/subscriptions/company/{id}` | `SUPER_ADMIN`, `COMPANY_ADMIN` (tenant sendiri) |
| `PUT` | `/api/v1/subscriptions/company/{id}/plan` | `SUPER_ADMIN` |
| `PUT` | `/api/v1/subscriptions/company/{id}/cancel` | `SUPER_ADMIN` |
| `PUT` | `/api/v1/subscriptions/company/{id}/reactivate` | `SUPER_ADMIN` |
| `GET` | `/api/v1/invoices` | `SUPER_ADMIN` |
| `GET` | `/api/v1/invoices/me` | `COMPANY_ADMIN`, `HR` |
| `PUT` | `/api/v1/invoices/{id}/pay` | `SUPER_ADMIN` |
| `PUT` | `/api/v1/invoices/{id}/void` | `SUPER_ADMIN` |
| `POST` | `/api/v1/invoices/billing-cycle/run` | `SUPER_ADMIN` |
| `GET` | `/api/v1/admin/dashboard` | `SUPER_ADMIN` |

### 9.19 HTTP status dan error code

| Status | Kapan | Contoh `errorCode` |
|---|---|---|
| 400 | Format request salah | `BAD_REQUEST` |
| 401 | Token tidak ada / tidak valid / kredensial salah | `INVALID_CREDENTIALS`, `INVALID_TOKEN`, `TOKEN_EXPIRED` |
| 403 | Role tidak cukup, akun/tenant nonaktif, akses lintas tenant | `FORBIDDEN`, `USER_INACTIVE`, `COMPANY_INACTIVE`, `CROSS_TENANT_ACCESS_DENIED`, `ROLE_NOT_GRANTABLE`, `CANNOT_REVIEW_OWN_REQUEST`, `NOT_REQUEST_OWNER` |
| 404 | Data tidak ditemukan | `COMPANY_NOT_FOUND`, `USER_NOT_FOUND`, `EMPLOYEE_NOT_FOUND`, `DEPARTMENT_NOT_FOUND`, `POSITION_NOT_FOUND`, `SHIFT_NOT_FOUND`, `ATTENDANCE_NOT_FOUND`, `LOCATION_NOT_FOUND`, `LEAVE_NOT_FOUND`, `CORRECTION_NOT_FOUND`, `PLAN_NOT_FOUND`, `SUBSCRIPTION_NOT_FOUND`, `INVOICE_NOT_FOUND`, `NO_EMPLOYEE_PROFILE` |
| 409 | Duplikat atau data masih dipakai | `COMPANY_CODE_ALREADY_EXISTS`, `USER_EMAIL_ALREADY_EXISTS`, `EMPLOYEE_CODE_ALREADY_EXISTS`, `DEPARTMENT_NAME_ALREADY_EXISTS`, `DEPARTMENT_IN_USE`, `POSITION_IN_USE`, `SHIFT_NAME_ALREADY_EXISTS`, `SHIFT_IN_USE`, `SHIFT_ASSIGNMENT_ALREADY_EXISTS`, `EMPLOYEE_ALREADY_HAS_ACCOUNT`, `ALREADY_CHECKED_IN`, `ALREADY_CHECKED_OUT`, `LOCATION_NAME_ALREADY_EXISTS`, `LEAVE_ALREADY_EXISTS`, `LEAVE_NOT_PENDING`, `CORRECTION_ALREADY_EXISTS`, `CORRECTION_NOT_PENDING`, `PLAN_CODE_ALREADY_EXISTS`, `PLAN_IN_USE`, `INVOICE_ALREADY_SETTLED` |
| 422 | Validasi atau aturan bisnis gagal | `VALIDATION_ERROR`, `INVALID_TIMEZONE`, `EMPLOYEE_INACTIVE`, `INVALID_SHIFT`, `NO_SHIFT_ASSIGNED`, `INVALID_DATE_RANGE`, `NOT_CHECKED_IN`, `CHECK_OUT_BEFORE_CHECK_IN`, `OUTSIDE_GEOFENCE`, `LOCATION_REQUIRED`, `NO_ACTIVE_LOCATION`, `INVALID_CORRECTION` |
| 402 | Batas plan tercapai atau langganan tidak aktif | `EMPLOYEE_LIMIT_REACHED`, `LOCATION_LIMIT_REACHED`, `USER_LIMIT_REACHED`, `FEATURE_NOT_IN_PLAN`, `SUBSCRIPTION_NOT_ACTIVE` |
| 500 | Kesalahan tak terduga | `INTERNAL_ERROR` |

---

## 10. Docker

Berkas terkait:

```
Dockerfile            build multi-stage (Maven -> JRE 21), berjalan sebagai non-root
.dockerignore
docker-compose.yml    service: mysql, backend, nginx (profil with-nginx)
docker/nginx/nginx.conf
```

Perintah yang sering dipakai:

```bash
docker compose up -d --build        # build ulang lalu jalankan
docker compose ps                   # status service
docker compose logs -f backend      # log aplikasi
docker compose exec mysql \
  mysql -u attendance -p attendance_saas   # shell MySQL
docker compose restart backend
docker compose down                 # stop (data tetap ada)
docker compose down -v              # stop + hapus volume data
```

`backend` baru start setelah healthcheck MySQL hijau, jadi Flyway tidak pernah
menabrak database yang belum siap.

---

## 11. Struktur proyek

```
src/main/java/com/attendance/saas/
├── config/         JwtProperties, CorsProperties, OpenApiConfig, ClockConfig
├── controller/     Auth, Company, Department, Position, Employee, Shift,
│                   EmployeeShift, Attendance, Location, Leave,
│                   AttendanceCorrection, Dashboard, Report, AuditLog,
│                   Plan, Subscription, Invoice, SystemDashboard
├── dto/            common/ (ApiResponse, PageResponse), auth/, company/,
│                   department/, position/, employee/, shift/, attendance/,
│                   location/, leave/, correction/, dashboard/, report/,
│                   audit/, billing/
├── entity/         BaseEntity, TenantEntity, Company, CompanySettings, User,
│                   RefreshToken, Department, Position, Employee, Shift,
│                   EmployeeShift, Attendance, Location, LeaveRequest,
│                   AttendanceCorrection, AuditLog, Plan, Subscription,
│                   Invoice, enums/
├── repository/     satu repository per entity, seluruh finder di-scope company
├── service/        AuthService, RefreshTokenService, CompanyService,
│                   CompanyRegistrationService, DepartmentService,
│                   PositionService, EmployeeService, EmployeeAccountService,
│                   ShiftService, EmployeeShiftService, ShiftScheduleResolver,
│                   AttendanceCalculator, AttendanceService,
│                   AttendanceQueryService, GeofenceService, LocationService,
│                   LeaveService, LeaveAttendanceMarker,
│                   AttendanceCorrectionService, DashboardService,
│                   ReportService, AuditService (tulis), AuditLogService (baca),
│                   PlanService, PlanLimitService, SubscriptionService,
│                   BillingService, SystemDashboardService
├── security/       SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter,
│                   UserPrincipal, TenantContext, SecurityUtils, handler REST
├── exception/      ErrorCode, ApiException + turunannya, GlobalExceptionHandler
├── mapper/         Company, User, Department, Position, Employee, Shift,
│                   Attendance, Location, Leave, AttendanceCorrection, Billing
├── specification/  Employee, Attendance, LeaveRequest, AttendanceCorrection
├── util/           TokenHasher, ShiftWindow, GeoUtils
└── AttendanceApplication.java
```

Aliran: `Controller -> Service -> Repository -> Database`. Business logic tidak
pernah berada di controller, dan entity tidak pernah diekspos langsung ke API.

---

## 12. Roadmap

| Phase | Isi | Status |
|---|---|---|
| 1 | Setup, MySQL, Flyway, Docker, JWT, Company, User, Role, multi-tenant security | **Selesai** |
| 2 | Employee, Department, Position, CRUD, pagination, search | **Selesai** |
| 3 | Shift, employee shift, attendance, check-in/out, late & early leave | **Selesai** |
| 4 | Geolocation, leave, approval, attendance correction | **Selesai** |
| 5 | Dashboard, report, audit log, Swagger lengkap | **Selesai** |
| 6 | Subscription, billing, company limit, plan management | **Selesai** |

### Yang sengaja belum dikerjakan

Hal-hal berikut di luar cakupan spesifikasi, dicatat agar tidak dikira sudah ada:

- **Integrasi payment gateway.** Lihat bagian 9.17; titik sambungnya sudah ada.
- **Penandaan `ABSENT` otomatis.** Status `ABSENT` dihitung saat query, bukan
  ditulis sebagai baris. Menuliskannya butuh scheduled job penutup hari.
- **Scheduler.** `POST /invoices/billing-cycle/run` masih dipanggil manual;
  belum ada `@Scheduled` yang menjalankannya harian.
- **Upload berkas.** Foto check-in disimpan sebagai URL atau storage key, bukan
  diunggah lewat API ini.
- **Notifikasi.** Tidak ada email atau push saat cuti disetujui atau tagihan
  terbit.
