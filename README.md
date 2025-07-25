# Vault4Tomcat

A lightweight **HashiCorp Vault** integration for **Apache Tomcat**.  
Securely fetch secrets at runtime, eliminate hardcoded credentials, and simplify Tomcat configuration.

![GitHub License](https://img.shields.io/github/license/dsoumis/Vault4Tomcat)
![Latest Release](https://img.shields.io/github/v/release/dsoumis/Vault4Tomcat)

## Introduction

**Vault4Tomcat** integrates **HashiCorp Vault** with **Apache Tomcat** to securely manage secrets in Tomcat configuration files.  

It dynamically resolves `${vault:path#key}` placeholders.

🔹 **Dynamic Secret Resolution** → Fetch secrets at runtime from HashiCorp Vault.  

**TODO:**  
🔹 **JNDI Integration**  
🔹 **Multi-Auth Support**  
🔹 **Secret Caching**  
🔹 **CLI Tool** 


**Security-First**: No more hardcoded passwords in configuration files.

---

## Installation

### Prerequisite: Have a Hashicorp Vault deployed
Example deployment:
```
podman run --cap-add=IPC_LOCK -e 'VAULT_DEV_ROOT_TOKEN_ID=my-root-token' -e 'VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200' -p 8200:8200 hashicorp/vault
```
in the container:
```
export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=my-root-token
vault kv put secret/myapp/config username="admin" password="s3cr3t"
vault kv get secret/myapp/config 
```
---
#### For AppRole authentication:

1. Enable the AppRole auth method:
    ```
    vault auth enable approle
    ```
2. Create a named role:
    ```
    vault write auth/approle/role/tomcat_role \
          token_type=batch \
          secret_id_ttl=10m \
          token_ttl=20m \
          token_max_ttl=30m \
          secret_id_num_uses=40
    ```
3. Fetch the RoleID of the AppRole:
    ```
    vault read auth/approle/role/tomcat_role/role-id
    ```
4. Get a SecretID issued against the AppRole:
    ```
    vault write -f auth/approle/role/tomcat_role/secret-id
    ```
5. Create a policy for the role:
   1. 
      ```
       vi tomcat.hcl
      ```
      ```
       path "secret/data/myapp/config"
       {
           capabilities = ["read", "sudo"]
       }
       ```
   2. Format the policy file:```vault policy fmt tomcat.hcl```
   3. Create a policy with the policy defined in file:```vault policy write tomcat tomcat.hcl```
6. Attach policy to role: 

   ```vault write -f auth/approle/role/tomcat_role token_policies="default,tomcat"```

#### For AWS IAM authentication:

1. Create an AWS Account & IAM User
   1. Sign in at: https://aws.amazon.com/
   2. Go to IAM → Users → Create User
      * Username: vault-test-user
      * Provide user access to the AWS Management Console → I want to create an IAM user
   3. Set permissions
      *  Choose "Attach policies directly"
      * Add: `AmazonEC2ReadOnlyAccess`
   4. Create the user → Select user → Security credentials → Create access key
      * Save the Access Key ID and Secret Access Key
2. Create an IAM Role for Vault
   1. Go to IAM → Roles → Create Role
   2. Trusted Entity Type: AWS service → EC2
   3. Skip permissions
   4. Role name: `VaultTestRole`
   5. After creating, get the Role ARN, e.g.: `arn:aws:iam::123456789012:role/VaultTestRole`
3. Attach Inline Policy to User
   1. Go to IAM → Users → vault-test-user → Permissions
   2. Click "Add Inline Policy"
   3. Use the JSON editor and paste this:
   ```
   {
       "Version": "2012-10-17",
       "Statement": [
           {
               "Sid": "Statement1",
               "Effect": "Allow",
               "Action": "iam:GetRole",
               "Resource": "<Role ARN>"
           },
           {
               "Sid": "Statement2",
               "Effect": "Allow",
               "Action": "iam:GetUser",
               "Resource": "<Role ARN>"
           }
       ]
   }
   ```
   4. Give it a name like AllowGetRoleForVault, and save.
   
4. Enable the AWS auth method:
    ```
    vault auth enable aws
    ```
5. Configure the credentials required to make AWS API calls:
   ```
    vault write auth/aws/config/client secret_key=<secret_key> access_key=<access_key>
    ```
6. Create a policy for the role:
   1.
   ```
    vi tomcat.hcl
   ```
   ```
    path "secret/data/myapp/config"
    {
        capabilities = ["read", "sudo"]
    }
    ```
   2. Format the policy file:```vault policy fmt tomcat.hcl```
   3. Create a policy with the policy defined in file:```vault policy write tomcat tomcat.hcl```
7. Configure the policies on the role
   ```
    vault write auth/aws/role/dev-role-iam auth_type=iam bound_iam_principal_arn=<Role ARN> policies=default,tomcat max_ttl=500h
   ```
8. Configure a required X-Vault-AWS-IAM-Server-ID header (recommended)
   ```
   vault write auth/aws/config/client iam_server_id_header_value=vault.example.com
   ```
9. (Optional) Verify successful login:
   ```
   vault login -method=aws header_value=vault.example.com role=dev-role-iam aws_access_key_id=<access_key> aws_secret_access_key=<secret_key>
   ```
### 1. Download the latest release:
[Vault4Tomcat Releases](https://github.com/dsoumis/Vault4Tomcat/releases)

### 2. Drop the JAR into Tomcat’s `lib/` directory:
```
cp vault4tomcat.jar $CATALINA_BASE/lib/
```
### 3. Define environment variables or create a `conf/vault.properties` file:
Token authentication example:
```
vault.address=<http://127.0.0.1:8200>
vault.token=<my-root-token>
vault.ssl.verify=true
```
AppRole authentication example:
```
vault.address=<http://127.0.0.1:8200>
vault.auth.method=approle
vault.auth.approle.role_id=<roleId>
vault.auth.approle.secret_id=<secretId>
```

### 4. Register the Property Source in `conf/catalina.properties`:
```
org.apache.tomcat.util.digester.PROPERTY_SOURCE=com.mycompany.tomcat.VaultPropertySource
```

### 5. Use Vault secrets in `.xml` files:
```
<Resource name="jdbc/MyDB" auth="Container" type="javax.sql.DataSource"
username="myuser"
password="${vault:database/creds#password}"
driverClassName="com.mysql.cj.jdbc.Driver"
url="jdbc:mysql://dbhost/mydb"/>
```

---

## How It Works
Vault4Tomcat intercepts `${vault:path#key}` placeholders in Tomcat config files and replaces them with secrets from HashiCorp Vault.

1. Tomcat starts up and loads configuration.  
2. VaultPropertySource detects placeholders like ${vault:...}.  
3. It queries Vault via VaultClient and retrieves secrets.  
4. Secrets are injected into Tomcat’s configuration at runtime.  
5. Secret caching reduces Vault API calls for performance.  

## Licensing
Vault4Tomcat is open-source and licensed under the Apache License 2.0.

## Contributing
To submit a GitHub Pull Request you'll need to fork the repository, clone your fork to do the work:

$ git clone https://github.com/$USERNAME/Vault4Tomcat.git

and then push your changes, and submit a Pull Request via the GitHub UI.