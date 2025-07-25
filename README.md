# Keycloak QQ IdentityProvider (Token Exchange Support)

本專案是一個 Keycloak 擴充套件，實作 QQ OAuth 作為 Identity Provider，支援 QQ authorization code 及 QQ id_token 的 [Token Exchange](https://datatracker.ietf.org/doc/html/rfc8693)。

> 讓用戶端可直接透過 Keycloak `/realms/{realm}/protocol/openid-connect/token` 端點，將 QQ code 或 id_token 兌換成 Keycloak access token/id token。

---

## 功能特色

- 支援 QQ 第三方登入（OAuth2.0）
- 完整支援 [Token Exchange Grant](https://datatracker.ietf.org/doc/html/rfc8693) 標準流程
- 使用者可用 QQ 的 `code` 或 `id_token` 換取 Keycloak token
- 會自動 mapping QQ 用戶資料為 Keycloak User，首次登入自動建立帳號
- 完整錯誤處理與安全欄位驗證（如 openid、ret、JWT 格式等）

---

## 專案架構

```
qq-identity-provider-keycloak/
├── .gitignore
├── README.md
├── pom.xml
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── johnsonfitness/
│                   └── provider/
│                       └── qq/
│                           ├── QQIdentityProvider.java
│                           └── QQIdentityProviderFactory.java
└── resources/
    └── META-INF/
        └── services/
            └── org.keycloak.broker.oidc.OIDCIdentityProviderFactory
```

---

## 安裝與建置

### 1. Clone 專案
```bash
git clone <your-repo-url>
cd qq-identity-provider-keycloak
```

### 2. 建置 Jar
需安裝 JDK 17 與 Maven。編譯：
```bash
mvn clean package
```
產物位於 `target/qq-identity-provider-*.jar`

### 3. 部署到 Keycloak
將 jar 檔放到 Keycloak server 的：
- `standalone/deployments/` (Wildfly)
- `providers/` (Quarkus)

重啟 Keycloak。

---

## 在 Keycloak Admin Console 設定 QQ Provider

1. 到 `Identity Providers > Add provider > QQ` (你自訂的 id)
2. 輸入 QQ 應用的 client id / secret
3. 其餘依需求設定即可

---

## Token Exchange 使用方式

### 一般登入/註冊
- 用戶在前端導引至 QQ 登入，授權後回傳 code 或 id_token
- 前端呼叫 Keycloak：

#### 用 code
```bash
POST /realms/{realm}/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:token-exchange&
subject_token={qq_code}&
subject_token_type=authorization_code&
provider=qq&
client_id={keycloak-client-id}&
client_secret={keycloak-client-secret}
```

成功後會收到 Keycloak 的 token response。

---

## 開發重點
- 支援 QQ code 換 access_token，再取得 openid/userinfo
- 支援 QQ id_token 解析（含 JWT base64 decode、必要欄位驗證）
- 錯誤皆自動回傳明確 HTTP code 與錯誤訊息
- 首次登入會自動建立 Keycloak user，且以 QQ openid 當 username

---

## 測試範例

- 請用 curl 或 Postman 直接測試 `/token` 端點。
- 若要在生產用，建議再加強 id_token JWT 簽章驗證與 exp/iss/aud 檢查。

---

## 參考資源
- [Keycloak Token Exchange 官方文件](https://www.keycloak.org/docs/latest/server_development/#_token-exchange)
- [QQ 互聯開發文檔](https://wiki.connect.qq.com/)
- [apple-identity-provider-keycloak (github)](https://github.com/klausbetz/apple-identity-provider-keycloak)
- [OAuth 2.0 Token Exchange RFC8693](https://datatracker.ietf.org/doc/html/rfc8693)
