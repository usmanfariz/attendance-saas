package com.attendance.saas.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI attendanceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Attendance SaaS API")
                        .version("v1")
                        .description("""
                                REST API untuk aplikasi SaaS absensi karyawan multi-tenant.

                                **Autentikasi.** Semua endpoint kecuali login, refresh token, dan
                                registrasi perusahaan membutuhkan header
                                `Authorization: Bearer <access token>`. Klik **Authorize** di kanan
                                atas, lalu tempel access token tanpa prefix `Bearer`.

                                **Isolasi tenant.** `company_id` selalu diambil dari JWT, tidak
                                pernah dari body maupun query parameter. Mengetahui id milik
                                perusahaan lain tidak memberi akses apa pun.

                                **Waktu.** Seluruh logika absensi memakai timezone perusahaan
                                (`companies.timezone`), bukan timezone server. Timestamp dikirim
                                dalam UTC dan disertai versi jam lokal perusahaan.

                                **Format response.** Setiap endpoint memakai amplop yang sama:
                                `{ success, message, data, errorCode, errors, timestamp }`.
                                Endpoint list mengembalikan `data` berisi
                                `{ data, page, size, totalElements, totalPages, first, last }`.
                                """)
                        .contact(new Contact().name("Attendance SaaS"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url("/").description("Current server")))
                // Single source of truth for tag names and descriptions: a
                // divergent @Tag description on a controller makes springdoc
                // publish the same tag twice.
                .tags(List.of(
                        tag("Authentication", "Login, refresh token, logout, dan registrasi perusahaan"),
                        tag("Company", "Manajemen perusahaan (tenant) dan profilnya"),
                        tag("Department", "Departemen dalam satu perusahaan"),
                        tag("Position", "Jabatan atau posisi dalam satu perusahaan"),
                        tag("Employee", "Data karyawan dan akun loginnya"),
                        tag("Shift", "Jam kerja, termasuk shift malam lintas hari"),
                        tag("Employee Shift", "Penjadwalan shift per karyawan per tanggal"),
                        tag("Attendance", "Check-in, check-out, dan riwayat absensi"),
                        tag("Attendance Correction", "Koreksi absensi dan persetujuannya"),
                        tag("Leave", "Pengajuan dan persetujuan cuti, sakit, dan izin"),
                        tag("Location", "Lokasi kantor dan pengaturan geofencing"),
                        tag("Dashboard", "Ringkasan absensi perusahaan dan karyawan"),
                        tag("Report", "Laporan absensi harian, bulanan, dan per karyawan"),
                        tag("Audit Log", "Jejak aktivitas penting dalam perusahaan"),
                        tag("Plan", "Katalog paket langganan platform"),
                        tag("Subscription", "Langganan perusahaan dan pemakaian kuotanya"),
                        tag("Invoice", "Tagihan langganan dan siklus penagihan"),
                        tag("System Admin", "Dashboard lintas tenant untuk super admin")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .name(BEARER_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Masukkan access token JWT tanpa prefix 'Bearer'")));
    }

    private Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }
}
