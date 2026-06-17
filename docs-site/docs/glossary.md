# 名詞解釋

按英文字母排序。

## A

**Account / 帳戶**
：現實中你的一個資金容器。一個使用者可以有多個帳戶。詳見 [帳戶管理](user-guide/accounts.md)。

**Actuator**
：Spring Boot 提供的監控端點，本系統用 `/actuator/health` 給 Render 健康檢查。

## B

**BCrypt**
：一種「故意算很慢」的密碼雜湊演算法，能抵抗 brute-force。本系統用 Spring 預設 strength 10。

**Bearer Token**
：HTTP 驗證方式，header 寫 `Authorization: Bearer <token>`。

## C

**CORS (Cross-Origin Resource Sharing)**
：瀏覽器同源政策的鬆綁機制。前後端不同網域時，後端要明確允許。

**CRUD**
：Create / Read / Update / Delete，資料的基本操作四件套。

## F

**Flyway**
：資料庫版本控管工具。每支 SQL migration 跑一次後寫進 `flyway_schema_history`，下次跳過。

## I

**Integration Test (IT)**
：啟動完整應用 + 真實 DB 跑的測試。本系統用 `*IT.java` 命名。

## J

**JPA (Jakarta Persistence API)**
：Java 標準的 ORM 介面，本系統用 Hibernate 實作。

**JWT (JSON Web Token)**
：自帶簽章的 token，前端拿著它證明「我是某人」。

## M

**Migration**
：對資料庫 schema 的一次變更（建表、加欄位、加索引）。

**Monorepo**
：所有相關專案放在同一個 git repo（本系統：`backend/`、`frontend/`、`docs/`）。

## O

**OCR (Optical Character Recognition)**
：從圖片辨識文字。Sprint 4 會用來解析收據。

**OpenAPI / Swagger**
：API 規格描述格式 + 互動式文件 UI。本系統用 Springdoc 自動產生。

## P

**Principal**
：Spring Security 中「目前是誰」的物件。本系統是 `AuthenticatedUser(id, email)`。

## R

**Render**
：雲端 PaaS。本系統部署目標。

**Repository (Pattern)**
：把資料存取封裝起來，service 只跟介面說話。Spring Data JPA 自動產生實作。

## S

**Spring Boot**
：Java 最主流的 web 框架，自動配置一堆東西。

**Sprint**
：Scrum 中的固定時長迭代（本系統用 2 週）。

## T

**Testcontainers**
：用 Docker 跑真實服務（DB、Redis、Kafka...）給測試用的 library。比 mock 可靠很多。

## V

**Vite**
：前端 build tool，啟動超快，Sprint 1 之後會用。
