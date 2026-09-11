#!/usr/bin/env bash
#
# Menjalankan backend di mesin pengembangan, tanpa Docker.
#
# Dibuat karena langkah di README §6.1 mengasumsikan JDK 21 terpasang sistem
# (`/usr/lib/jvm/...`) dan Maven ada di PATH. Pada mesin tanpa akses root,
# keduanya biasanya dipasang user-local — skrip ini menemukannya sendiri lalu
# menyiapkan seluruh environment variable yang dibutuhkan aplikasi.
#
# Pemakaian:
#   ./run-local.sh              # profil dev, port 8080
#   ./run-local.sh prod         # profil prod
#   SERVER_PORT=9090 ./run-local.sh
#
set -euo pipefail

cd "$(dirname "$0")"

PROFILE="${1:-dev}"
DEV_TOOLS="${DEV_TOOLS:-$HOME/.local/share/dev-tools}"

# --- Toolchain --------------------------------------------------------------
# JDK user-local lebih diutamakan: build gagal dengan "release version 21 not
# supported" bila Maven terlanjur memakai JDK 17 bawaan sistem.
if [[ -z "${JAVA_HOME:-}" ]]; then
  for candidate in "$DEV_TOOLS"/jdk-21* /usr/lib/jvm/java-21-openjdk-amd64; do
    if [[ -x "$candidate/bin/java" ]]; then
      JAVA_HOME="$candidate"
      break
    fi
  done
fi

if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "GAGAL: JDK 21 tidak ditemukan." >&2
  echo "       Pasang di $DEV_TOOLS/jdk-21... atau set JAVA_HOME sendiri." >&2
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

# Maven: pakai wrapper bila ada, lalu Maven user-local, lalu yang di PATH.
if [[ -x ./mvnw ]]; then
  MVN=./mvnw
elif MAVEN_HOME=$(ls -d "$DEV_TOOLS"/apache-maven-* 2>/dev/null | tail -1) \
     && [[ -x "$MAVEN_HOME/bin/mvn" ]]; then
  export PATH="$MAVEN_HOME/bin:$PATH"
  MVN=mvn
elif command -v mvn >/dev/null 2>&1; then
  MVN=mvn
else
  echo "GAGAL: Maven tidak ditemukan (cek $DEV_TOOLS/apache-maven-*)." >&2
  exit 1
fi

# --- Database ---------------------------------------------------------------
# `.env` memakai DB_HOST=mysql, yaitu nama service di docker-compose.yml. Untuk
# run lokal itu tidak dapat di-resolve, jadi defaultnya di sini adalah loopback.
export SPRING_PROFILES_ACTIVE="$PROFILE"
export DB_HOST="${DB_HOST:-127.0.0.1}"
export DB_PORT="${DB_PORT:-3306}"
export DB_NAME="${DB_NAME:-attendance_saas}"
export DB_USERNAME="${DB_USERNAME:-attendance}"
export DB_PASSWORD="${DB_PASSWORD:-attendance}"

export SERVER_PORT="${SERVER_PORT:-8080}"
export SWAGGER_ENABLED="${SWAGGER_ENABLED:-true}"

# Secret pengembangan yang stabil, supaya token tidak hangus tiap restart —
# yang akan memaksa login ulang di aplikasi mobile setiap kali. JANGAN dipakai
# di deployment mana pun; di production isi JWT_SECRET dari environment.
export JWT_SECRET="${JWT_SECRET:-local-dev-secret-not-for-production-0123456789abcdefghijklmnopqrstuvwxyz}"

# Gagal lebih awal dengan pesan yang jelas, daripada menunggu stack trace Hikari.
if command -v mysqladmin >/dev/null 2>&1; then
  if ! mysqladmin ping -h "$DB_HOST" -P "$DB_PORT" --silent >/dev/null 2>&1; then
    echo "GAGAL: MySQL tidak merespons di $DB_HOST:$DB_PORT." >&2
    echo "       Nyalakan dulu, misal: sudo systemctl start mysql" >&2
    exit 1
  fi
fi

# Port yang sudah terpakai biasanya berarti instance lama masih hidup.
if command -v ss >/dev/null 2>&1 && ss -ltn 2>/dev/null | grep -q ":$SERVER_PORT "; then
  echo "GAGAL: port $SERVER_PORT sudah dipakai proses lain." >&2
  echo "       Hentikan dulu, atau jalankan dengan SERVER_PORT=9090 $0" >&2
  exit 1
fi

echo "JDK     : $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
echo "Profil  : $SPRING_PROFILES_ACTIVE"
echo "Database: $DB_USERNAME@$DB_HOST:$DB_PORT/$DB_NAME"
echo "HTTP    : http://localhost:$SERVER_PORT  (Swagger: /swagger-ui.html)"
echo

exec "$MVN" spring-boot:run -Dspring-boot.run.profiles="$SPRING_PROFILES_ACTIVE"
