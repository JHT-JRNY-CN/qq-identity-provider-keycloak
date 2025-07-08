# QQ Identity Provider for Keycloak

This project is a custom **QQ OAuth2 Identity Provider extension for Keycloak 26.2.5**.  
It allows users to log in via their QQ accounts using the standard OAuth2 Authorization Code Flow and supports Token Exchange and user info retrieval via REST endpoints.



## 🔧 Features

- ✅ Supports QQ OAuth2 Authorization Code login
- ✅ Token Exchange support for federated identities
- ✅ Provides custom REST endpoints (`/userinfo` and `/token-exchange`)
- ✅ Easily configurable through the Keycloak Admin Console
- ✅ Fully integrated as a Keycloak Identity Provider



## 📁 Project Structure
```
qq-identity-provider/
├── src/
│ ├── main/java/com/johnsonfitness/qq/
│ │ ├── QQIdentityProvider.java
│ │ ├── QQIdentityProviderConfig.java
│ │ ├── QQIdentityProviderFactory.java
│ │ ├── QQIdentityProviderEndpoint.java
│ │ └── QQIdentityProviderRestFactory.java
│ └── resources/META-INF/services/
│ └── org.keycloak.services.resource.RealmResourceProviderFactory
├── pom.xml
└── README.md
```

---

## ⚙️ Build & Install

### 1. Build the JAR

Use Maven to compile and package:

```bash
mvn clean package
```

The JAR will be located in:

```bash
target/qq-identity-provider-*.jar
```

### 2. Deploy to Keycloak
Copy the JAR into the Keycloak providers/ directory:

```bash
cp target/qq-identity-provider-*.jar /opt/keycloak/providers/
```

Rebuild Keycloak:

```bash
cd /opt/keycloak
bin/kc.sh build
```

Then start Keycloak:

```bash
bin/kc.sh start-dev
```

## 🔑 QQ OAuth2 Configuration
Apply for QQ Open Platform credentials:

| Parameter |	Description |
| ------ | ------ |
| Client ID	| App ID from QQ Connect |
| Client Secret |	App Key |
| Redirect URI | Should match Keycloak broker URL, e.g. https://{host}/realms/{realm}/broker/qq/endpoint |

## 🧩 Configure in Keycloak Admin Console
1. Log into the Keycloak Admin Console
2. Choose your realm
3. Go to Identity Providers
4. Click Add provider → Select QQ
5. Fill in:
  - Client ID
  - Client Secret
  - Default Scopes: get_user_info
6. Save and test QQ login

## 🔌 Custom REST Endpoints
`/userinfo`
Get QQ user info using the access token:

```http
GET /realms/{realm}/qq-endpoint/userinfo?access_token=xxx
```

`/token-exchange`
Implements Token Exchange compatible with Keycloak:

```http
POST /realms/{realm}/qq-endpoint/token-exchange
Content-Type: application/json

{
  "grant_type": "urn:ietf:params:oauth:grant-type:token-exchange",
  "subject_token": "xxx",
  "subject_issuer": "qq"
}
```

## 🛠  Development Notes
- Built for *Keycloak 26.2.5*
- Based on *Quarkus / Jakarta EE (JDK 17+)*
- Custom endpoints are exposed using `RealmResourceProviderFactory`
- The main provider class extends `AbstractOAuth2IdentityProvider`
- Ensure `QQIdentityProviderConfig` is created manually (not injected)
