# ApexStock

ApexStock is a Spring Boot REST API for inventory management across products, customers, warehouses, and stock locations.

The backend supports account registration and login, JWT authentication, CRUD operations, stock adjustments and transfers, order processing, movement history, low-stock alerts, CSV imports, and demand-based reorder recommendations.

The frontend is maintained separately and is not included in this repository.

## Live Deployment

- Backend: https://inventory-management-n8sc.onrender.com
- Swagger UI: https://inventory-management-n8sc.onrender.com/swagger-ui/index.html

## Features

### Authentication and accounts

- Register and log in with email and password
- BCrypt password hashing
- JWT authentication with one-hour token lifetime
- Stateless Spring Security configuration
- Access restricted to authenticated users
- Data isolated by the authenticated account

### Product management

- Create, list, retrieve, update, and delete products
- Import products from CSV
- Product fields include SKU, name, description, reorder level, lead time, and ownership

### Customer management

- Create, list, and retrieve customers
- Associate customers with the authenticated account

### Warehouse management

- Create, list, retrieve, update, and deactivate warehouses
- Import warehouses from CSV
- Enforce warehouse request validation

### Inventory management

- Initialize stock for a product and warehouse
- Adjust stock using inbound or outbound movements
- Transfer stock between warehouses
- Prevent stock quantities from dropping below zero
- List inventory, optionally filtered by warehouse
- Retrieve stock for a product at a specific warehouse
- Return low-stock inventory based on configured reorder levels
- Record stock-movement history
- Import initial inventory items from CSV

### Order management

- Create orders for a customer
- Add multiple product and warehouse items to an order
- Validate positive quantities and overflow-safe combined quantities
- Remove stock during order creation
- Record outbound stock movements
- List, retrieve, and cancel orders
- Restore stock and record inbound movements when an order is cancelled
- Prevent concurrent orders from allocating more stock than is available

### Reorder recommendations

- Calculate reorder recommendations from outbound movement history
- Configure the demand window, service level, default lead time, cover days, minimum demand days, and time zone
- Allow an individual product lead time to override the default
- Calculate mean demand, demand standard deviation, lead-time demand, safety stock, reorder point, days of cover, and suggested quantity
- Classify recommendations as `CRITICAL`, `REORDER`, `OK`, or `NO_DATA`
- Filter recommendations by warehouse and status
- Return actionable reorder alerts

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4 |
| Web API | Spring MVC REST controllers |
| Security | Spring Security, JWT, BCrypt |
| Persistence | Spring Data JPA, PostgreSQL |
| API documentation | SpringDoc OpenAPI, Swagger UI |
| CSV processing | Apache Commons CSV |
| Testing | JUnit 5, Mockito, Spring Boot Test, Testcontainers |
| Load testing | Grafana k6 |
| Local database | PostgreSQL 16 with Docker Compose |
| Deployment | Docker and Render |

## Architecture

```text
Separate frontend
        |
        | HTTPS REST requests
        | Bearer JWT
        v
Spring Boot API
        |
        +--> Spring Security
        |
        +--> REST Controllers
        |
        +--> Business Services
        |
        +--> Spring Data JPA
        |
        v
     PostgreSQL
        |
        +--> Accounts
        +--> Products
        +--> Customers
        +--> Warehouses
        +--> Inventory Items
        +--> Stock Movements
        +--> Orders
```

## Core Business Flow

### Create an order

1. The frontend sends `POST /order` with a customer ID and order items.
2. `OrderController` receives the request.
3. `OrderService` validates the customer and all product/warehouse quantities.
4. Product, warehouse, and inventory records are checked against the authenticated account.
5. `InventoryitemRepository` uses a pessimistic write lock to protect the inventory row.
6. The service verifies enough stock exists and deducts the quantity.
7. An outbound `StockMovement` is recorded.
8. The order and order items are saved in the same transaction.
9. The API returns an order response.

### Cancel an order

1. The frontend sends `PUT /order/{id}/cancel`.
2. The order is locked and checked for ownership and status.
3. Only pending orders can be cancelled.
4. Inventory quantities are restored for each order item.
5. Inbound stock movements are recorded.
6. The order status is changed to `CANCELLED`.

### Calculate reorder recommendations

1. The API reads outbound movements for the configured demand window.
2. Daily demand is grouped by inventory item.
3. `ReorderCalculator` calculates mean demand and standard deviation.
4. Lead-time demand and safety stock are calculated from the configured service level.
5. The reorder point, days of cover, and suggested quantity are returned.

## API Summary

The complete request and response definitions are available in Swagger UI.

| Area | Main endpoints |
|---|---|
| Authentication | `POST /auth/register`, `POST /auth/login`, `GET /users/me` |
| Products | `POST /product`, `GET /product`, `PUT /product/{id}`, `DELETE /product/{id}` |
| Customers | `POST /customer`, `GET /customer`, `GET /customer/{id}` |
| Warehouses | `POST /warehouse`, `GET /warehouse`, `PUT /warehouse/{id}`, `DELETE /warehouse/{id}` |
| Inventory | `POST /inventory/initial-stock`, `POST /inventory/adjustments`, `POST /inventory/transfers` |
| Inventory reads | `GET /inventory`, `GET /inventory/low-stock`, `GET /inventory/{productId}/{warehouseId}` |
| Stock movements | `GET /inventory/{productId}/{warehouseId}/movements` |
| Orders | `POST /order`, `GET /order`, `GET /order/{id}`, `PUT /order/{id}/cancel` |
| Reorders | `GET /api/stock/reorder-recommendations`, `GET /api/stock/reorder-recommendations/alerts` |

CSV endpoints are also provided for products, warehouses, and initial stock.

## Concurrency and Data Integrity

- Order creation and stock cancellation run in transactions.
- Inventory rows use pessimistic write locks during stock mutations.
- Stock checks prevent negative balances.
- Stock transfer operations validate source, destination, product, quantity, and ownership.
- Duplicate product/warehouse order quantities are merged with overflow protection.
- Stock movements preserve the quantity change and movement type.

## Security

- Authentication is required for application endpoints.
- Passwords must contain at least 12 characters.
- JWT secrets must decode to at least 32 bytes.
- JWT issuer validation is enabled.
- CORS is restricted to configured frontend origins.
- Database credentials and JWT secrets are supplied through environment variables.
- Load-test credentials are not stored in the source.

## Local Development

### Prerequisites

- Java 21
- Maven Wrapper
- Docker Desktop
- Git

### Configure environment variables

Create a `.env` file or configure the variables in the IDE or terminal:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5434/inventory
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_database_password
POSTGRES_HOST_PORT=5434
JWT_SECRET=your_base64_encoded_secret
```

`JWT_SECRET` must be a Base64-encoded key that decodes to at least 32 bytes.

Do not commit real database passwords or JWT secrets.

### Start PostgreSQL

```powershell
docker compose up -d postgres
```

PostgreSQL will be available on `localhost:5434` with persistent data stored in the `postgres_data` Docker volume.

### Run the application

```powershell
.\mvnw.cmd spring-boot:run
```

The API normally runs at `http://localhost:8080`.

Swagger UI will be available at `http://localhost:8080/swagger-ui/index.html`.

## Testing

Run unit and integration tests with:

```powershell
.\mvnw.cmd test
```

The test suite includes:

- Reorder calculator tests
- Product, warehouse, stock, and order unit tests
- CSV import tests
- Warehouse request validation tests
- Account-isolation integration tests
- PostgreSQL integration tests using Testcontainers
- A concurrent-order test that verifies 100 requests cannot allocate more than 10 available units

The PostgreSQL integration test starts a PostgreSQL 16 container automatically. Docker Desktop must be running.

## Load Testing

A k6 load-test script is included in `load-test.js`.

It authenticates against `/auth/login` and sends warehouse requests through the staged load profile. The script configures:

- 100 virtual users
- 500 virtual users
- 1,000 virtual users
- Less than 1% failed requests
- 95th-percentile request duration below 500 ms

Set credentials through environment variables before running it:

```powershell
$env:BASE_URL="http://localhost:8080"
$env:LOAD_TEST_EMAIL="dedicated-test-account@example.com"
$env:LOAD_TEST_PASSWORD="dedicated-test-password"
k6 run load-test.js
```

Use a dedicated test account and only run load tests against an environment you are authorized to test.
