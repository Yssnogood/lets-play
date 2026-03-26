# lets-play

Spring Boot CRUD API using MongoDB, JWT authentication, role-based access control, and HTTPS in development.

## Overview

This repository contains a backend API located in `crud-api/`.

The application provides:

- User registration and login
- JWT-based authentication
- User management with role checks
- Product management with owner/admin permissions
- Password hashing with BCrypt
- Input validation with Jakarta Validation
- HTTPS enabled in development
- MongoDB persistence

## Tech Stack

- Java 17
- Spring Boot 3.5.10
- Spring Web
- Spring Security
- Spring Data MongoDB
- Jakarta Validation
- JWT via `jjwt`
- Lombok
- Maven Wrapper
- MongoDB

## Project Structure

```text
lets-play/
├── audit_check.sh
├── README.md
└── crud-api/
		├── mvnw
		├── pom.xml
		└── src/
				├── main/
				│   ├── java/com/example/crudapi/
				│   │   ├── config/
				│   │   ├── controller/
				│   │   ├── dto/
				│   │   ├── exception/
				│   │   ├── model/
				│   │   ├── repository/
				│   │   ├── security/
				│   │   └── service/
				│   └── resources/
				└── test/
```

## Requirements

Install or provide:

- Java 17
- MongoDB running on `localhost:27017`
- `curl` for manual API testing
- `jq` for readable JSON output in shell commands
- `mongosh` if you want to inspect the database from terminal

## Configuration

The application is configured in `crud-api/src/main/resources/application.properties`.

Default values:

- MongoDB URI: `mongodb://localhost:27017/cruddb`
- HTTPS port: `8443`
- HTTP redirect port: `8081`
- JWT secret: environment variable `JWT_SECRET`, with a development fallback
- JWT expiration: environment variable `JWT_EXPIRATION_MS`, default `3600000`

Recommended before starting the app:

```bash
export JWT_SECRET="a-very-long-random-secret-at-least-32-chars"
```

## Run the Project

From the repository root:

```bash
cd "crud-api"
chmod +x mvnw
./mvnw spring-boot:run
```

Or from the workspace absolute path:

```bash
cd "/lets-play/crud-api"
chmod +x mvnw
./mvnw spring-boot:run
```

Application URLs:

- HTTPS: `https://localhost:8443`
- HTTP: `http://localhost:8081` then redirected to HTTPS

## Test Admin Account

At startup, the application creates a test admin if it does not already exist.

Credentials:

- Email: `admin@test.com`
- Password: `admin123`
- Role: `ROLE_ADMIN`

## Authentication Flow

1. Register a user with `POST /api/auth/register`
2. Login with `POST /api/auth/login`
3. Extract the JWT token from the response
4. Send the token in the `Authorization` header:

```text
Authorization: Bearer <jwt>
```

## Main Endpoints

### Auth

#### Register

```http
POST /api/auth/register
Content-Type: application/json
```

Example body:

```json
{
	"name": "Normal User",
	"email": "user@test.com",
	"password": "password123"
}
```

#### Login

```http
POST /api/auth/login
Content-Type: application/json
```

Example body:

```json
{
	"email": "user@test.com",
	"password": "password123"
}
```

### Users

#### Get current user

```http
GET /api/users/me
Authorization: Bearer <jwt>
```

#### Get all users

```http
GET /api/users
Authorization: Bearer <admin-jwt>
```

Admin only.

#### Create user as admin

```http
POST /api/users
Authorization: Bearer <admin-jwt>
Content-Type: application/json
```

#### Update a user

```http
PUT /api/users/{id}
Authorization: Bearer <jwt>
Content-Type: application/json
```

Allowed for:

- the user themself
- an admin

#### Update self

```http
PATCH /api/users/me
Authorization: Bearer <jwt>
Content-Type: application/json
```

#### Delete self

```http
DELETE /api/users/me
Authorization: Bearer <jwt>
```

#### Delete a user by id

```http
DELETE /api/users/{id}
Authorization: Bearer <jwt>
```

Allowed for:

- the user themself
- an admin

### Products

#### Get all products

```http
GET /api/products
```

Public endpoint. No authentication required.

#### Create product

```http
POST /api/products
Authorization: Bearer <jwt>
Content-Type: application/json
```

Example body:

```json
{
	"name": "Test Product",
	"description": "Example description",
	"price": 49.99
}
```

#### Update product

```http
PUT /api/products/{id}
Authorization: Bearer <jwt>
Content-Type: application/json
```

Allowed for:

- the product owner
- an admin

#### Delete product

```http
DELETE /api/products/{id}
Authorization: Bearer <jwt>
```

Allowed for:

- the product owner
- an admin

## Example curl Commands

### Register

```bash
curl -sk -X POST https://localhost:8443/api/auth/register \
	-H "Content-Type: application/json" \
	-d '{"name":"Normal User","email":"user@test.com","password":"password123"}' | jq
```

### Login

```bash
curl -sk -X POST https://localhost:8443/api/auth/login \
	-H "Content-Type: application/json" \
	-d '{"email":"user@test.com","password":"password123"}' | jq
```

### Get public products

```bash
curl -sk https://localhost:8443/api/products | jq
```

### Get current user

```bash
curl -sk https://localhost:8443/api/users/me \
	-H "Authorization: Bearer <jwt>" | jq
```

## Security Notes

Implemented measures:

- Password hashing before persistence
- Stateless authentication with JWT
- Sensitive password field not returned in response DTOs
- Input validation on request payloads
- HTTPS enabled in development using the provided keystore
- HTTP connector redirects to HTTPS
- Role checks using Spring Security method security

Notes:

- `GET /api/products` is intentionally public
- User listing is restricted to admins
- Product update/delete is restricted to owner or admin

## Exception Handling

The application includes a global exception handler for:

- duplicate key errors, such as an existing email
- validation errors on request bodies

Typical responses include:

- `400 Bad Request`
- `403 Forbidden`
- `404 Not Found`

## Database Inspection

### With mongosh

```bash
mongosh
```

Then:

```javascript
use cruddb
db.users.find().pretty()
db.products.find().pretty()
```

## Tests

Run unit and Spring tests with:

```bash
cd crud-api
./mvnw test
```

## Audit Script

The repository also contains an audit helper script at the root:

```bash
cd "/lets-play"
bash audit_check.sh
```

The script checks:

- CRUD behavior for users and products
- authentication and role restrictions
- exception handling and status codes
- public access to `GET /api/products`
- security behaviors such as hidden passwords and HTTPS redirect
- code annotation usage in models, controllers, and security classes

## Known Behavior

- HTTPS is enabled by default for local development
- Self-signed or development certificates require `curl -k`
- User deletion also removes that user's products
