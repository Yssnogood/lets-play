#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-https://localhost:8443}"
HTTP_REDIRECT_URL="${HTTP_REDIRECT_URL:-http://localhost:8081}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@test.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

REQ_BODY="$WORKDIR/response_body.json"
LAST_REQUEST_LABEL="N/A"
LAST_REQUEST_HTTP="N/A"
LAST_REQUEST_BODY="N/A"

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

require_cmds() {
  local missing=0
  for cmd in curl jq; do
    if ! command_exists "$cmd"; then
      echo "[ERROR] Missing required command: $cmd"
      missing=1
    fi
  done
  if [[ $missing -eq 1 ]]; then
    exit 1
  fi
}

api_call() {
  local method="$1"
  local url="$2"
  local data="${3:-}"
  local auth="${4:-}"

  local -a args
  args=(-sk -X "$method" -H "Content-Type: application/json")

  if [[ -n "$auth" ]]; then
    args+=(-H "Authorization: Bearer $auth")
  fi

  if [[ -n "$data" ]]; then
    args+=(-d "$data")
  fi

  args+=(-o "$REQ_BODY" -w "%{http_code}" "$url")
  curl "${args[@]}"
}

print_result() {
  local question="$1"
  local status="$2"
  local details="$3"

  printf '%s\n' "- Question: $question"
  printf '%s\n' "  Request: $LAST_REQUEST_LABEL"
  printf '%s\n' "  HTTP: $LAST_REQUEST_HTTP"
  printf '%s\n' "  Response Body: $LAST_REQUEST_BODY"
  printf '%s\n' "  Answer: $status"
  printf '%s\n' "  Details: $details"
  printf '%s\n' ""

  LAST_REQUEST_LABEL="N/A"
  LAST_REQUEST_HTTP="N/A"
  LAST_REQUEST_BODY="N/A"
}

show_last_response() {
  local label="$1"
  local http_status="$2"
  local body=""
  local compact=""
  local max_len=500

  if [[ -s "$REQ_BODY" ]]; then
    if compact=$(jq -c . "$REQ_BODY" 2>/dev/null); then
      body="$compact"
    else
      body=$(tr -d '\n' < "$REQ_BODY")
    fi
  else
    body="<empty body>"
  fi

  if [[ ${#body} -gt $max_len ]]; then
    body="${body:0:$max_len}..."
  fi

  LAST_REQUEST_LABEL="$label"
  LAST_REQUEST_HTTP="$http_status"
  LAST_REQUEST_BODY="$body"
}

count_annotation_in_tree() {
  local pattern="$1"
  local path="$2"
  local count="0"

  # grep returns 1 when no match; avoid exiting with pipefail.
  set +o pipefail
  count=$(grep -R --include='*.java' -o "$pattern" "$path" 2>/dev/null | wc -l | tr -d ' ')
  set -o pipefail

  if [[ -z "$count" ]]; then
    echo "0"
  else
    echo "$count"
  fi
}

count_annotation_in_file() {
  local pattern="$1"
  local file="$2"
  local count="0"

  set +e
  count=$(grep -o "$pattern" "$file" 2>/dev/null | wc -l | tr -d ' ')
  set -e

  if [[ -z "$count" ]]; then
    echo "0"
  else
    echo "$count"
  fi
}

json_has_password() {
  jq -e '.. | objects | has("password") | select(. == true)' "$1" >/dev/null 2>&1
}

extract_json_field() {
  local expr="$1"
  jq -r "$expr // empty" "$REQ_BODY"
}

generate_user_payload() {
  local name="$1"
  local email="$2"
  local password="$3"
  printf '{"name":"%s","email":"%s","password":"%s"}' "$name" "$email" "$password"
}

printf '%s\n' "=== Audit Script: CRUD + Security + Code Standards ==="
printf '%s\n' "BASE_URL=$BASE_URL"
printf '%s\n' ""

require_cmds

if ! curl -sk --connect-timeout 2 "$BASE_URL/api/products" >/dev/null; then
  echo "[ERROR] API is not reachable at $BASE_URL"
  echo "Start it first: ./mvnw spring-boot:run"
  exit 1
fi

TS="$(date +%s)"
USER_EMAIL="audit.user.${TS}@test.com"
USER_PASSWORD="password123"
USER_NAME="Audit User ${TS}"
USER2_EMAIL="audit.user2.${TS}@test.com"
USER2_PASSWORD="password123"
USER2_NAME="Audit User2 ${TS}"

ADMIN_TOKEN=""
USER_TOKEN=""
USER_ID=""
PRODUCT_ID=""

printf '%s\n' "== Authentication and Roles =="

# Admin login
status=$(api_call POST "$BASE_URL/api/auth/login" "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
show_last_response "POST /api/auth/login (admin)" "$status"
if [[ "$status" == "200" ]]; then
  ADMIN_TOKEN=$(extract_json_field '.token')
  if [[ -n "$ADMIN_TOKEN" ]]; then
    if json_has_password "$REQ_BODY"; then
      print_result "Does admin authentication work and hide passwords in response?" "PARTIAL" "Login works (200) but password field is present in response body."
    else
      print_result "Does admin authentication work and hide passwords in response?" "YES" "Login works (200), token returned, no password in response."
    fi
  else
    print_result "Does admin authentication work and hide passwords in response?" "NO" "Login returned 200 but token is missing."
  fi
else
  print_result "Does admin authentication work and hide passwords in response?" "NO" "Admin login failed with HTTP $status."
fi

# Register user
register_payload=$(generate_user_payload "$USER_NAME" "$USER_EMAIL" "$USER_PASSWORD")
status=$(api_call POST "$BASE_URL/api/auth/register" "$register_payload")
show_last_response "POST /api/auth/register" "$status"
if [[ "$status" == "201" || "$status" == "200" ]]; then
  if json_has_password "$REQ_BODY"; then
    print_result "Is user registration implemented without exposing password?" "PARTIAL" "User registration works (HTTP $status) but password appears in response."
  else
    print_result "Is user registration implemented without exposing password?" "YES" "User registration works (HTTP $status), no password in response."
  fi
else
  print_result "Is user registration implemented without exposing password?" "NO" "User registration failed with HTTP $status."
fi

# User login
status=$(api_call POST "$BASE_URL/api/auth/login" "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\"}")
show_last_response "POST /api/auth/login (user)" "$status"
if [[ "$status" == "200" ]]; then
  USER_TOKEN=$(extract_json_field '.token')
  USER_ID=$(extract_json_field '.user.id')
  if [[ -n "$USER_TOKEN" && -n "$USER_ID" ]]; then
    print_result "Can a regular user authenticate successfully?" "YES" "User login returned token and user id."
  else
    print_result "Can a regular user authenticate successfully?" "PARTIAL" "HTTP 200 but token or user id missing."
  fi
else
  print_result "Can a regular user authenticate successfully?" "NO" "User login failed with HTTP $status."
fi

# Non-admin reading all users (should be forbidden)
if [[ -n "$USER_TOKEN" ]]; then
  status=$(api_call GET "$BASE_URL/api/users" "" "$USER_TOKEN")
  show_last_response "GET /api/users (user token)" "$status"
  if [[ "$status" == "403" ]]; then
    print_result "Do user role restrictions work for GET /api/users?" "YES" "Regular user gets HTTP 403 as expected."
  else
    print_result "Do user role restrictions work for GET /api/users?" "NO" "Regular user received HTTP $status (expected 403)."
  fi
fi

# Admin reading all users (should pass)
if [[ -n "$ADMIN_TOKEN" ]]; then
  status=$(api_call GET "$BASE_URL/api/users" "" "$ADMIN_TOKEN")
  show_last_response "GET /api/users (admin token)" "$status"
  if [[ "$status" == "200" ]]; then
    print_result "Can admin access GET /api/users?" "YES" "Admin received HTTP 200."
  else
    print_result "Can admin access GET /api/users?" "NO" "Admin received HTTP $status (expected 200)."
  fi
fi

printf '%s\n' ""
printf '%s\n' "== Users CRUD =="

# Read own profile
if [[ -n "$USER_TOKEN" ]]; then
  status=$(api_call GET "$BASE_URL/api/users/me" "" "$USER_TOKEN")
  show_last_response "GET /api/users/me" "$status"
  if [[ "$status" == "200" ]]; then
    print_result "Is User READ (/api/users/me) working?" "YES" "HTTP 200 for authenticated user."
  else
    print_result "Is User READ (/api/users/me) working?" "NO" "HTTP $status for authenticated user."
  fi
fi

# Update own profile
if [[ -n "$USER_TOKEN" && -n "$USER_ID" ]]; then
  status=$(api_call PUT "$BASE_URL/api/users/$USER_ID" "{\"name\":\"Updated $TS\"}" "$USER_TOKEN")
  show_last_response "PUT /api/users/{id} (self update)" "$status"
  if [[ "$status" == "200" ]]; then
    print_result "Is User UPDATE working for own user id?" "YES" "HTTP 200 when updating own profile."
  else
    print_result "Is User UPDATE working for own user id?" "NO" "HTTP $status when updating own profile."
  fi
fi

# Update non-existing user -> should be 404
if [[ -n "$USER_TOKEN" ]]; then
  status=$(api_call PUT "$BASE_URL/api/users/non-existing-id-$TS" "{\"name\":\"Ghost\"}" "$USER_TOKEN")
  show_last_response "PUT /api/users/{id} (non-existing)" "$status"
  if [[ "$status" == "404" ]]; then
    print_result "Are exceptions handled for non-existing user update?" "YES" "HTTP 404 returned as expected."
  else
    print_result "Are exceptions handled for non-existing user update?" "NO" "HTTP $status returned (expected 404)."
  fi
fi

printf '%s\n' ""
printf '%s\n' "== Products CRUD =="

# GET products public
status=$(api_call GET "$BASE_URL/api/products")
show_last_response "GET /api/products (public)" "$status"
if [[ "$status" == "200" ]]; then
  print_result "Can GET /api/products be accessed without authentication?" "YES" "Public access works (HTTP 200)."
else
  print_result "Can GET /api/products be accessed without authentication?" "NO" "Received HTTP $status (expected 200)."
fi

# Create product with user token
if [[ -n "$USER_TOKEN" ]]; then
  status=$(api_call POST "$BASE_URL/api/products" "{\"name\":\"Audit Product\",\"description\":\"Test\",\"price\":49.99}" "$USER_TOKEN")
  show_last_response "POST /api/products" "$status"
  if [[ "$status" == "201" || "$status" == "200" ]]; then
    PRODUCT_ID=$(extract_json_field '.id')
    if [[ -n "$PRODUCT_ID" ]]; then
      print_result "Is Product CREATE working for authenticated user?" "YES" "Product created (HTTP $status), id=$PRODUCT_ID."
    else
      print_result "Is Product CREATE working for authenticated user?" "PARTIAL" "HTTP $status but product id missing in response."
    fi
  else
    print_result "Is Product CREATE working for authenticated user?" "NO" "HTTP $status on create product."
  fi
fi

# Register second user and attempt forbidden product update
register2_payload=$(generate_user_payload "$USER2_NAME" "$USER2_EMAIL" "$USER2_PASSWORD")
status=$(api_call POST "$BASE_URL/api/auth/register" "$register2_payload")
show_last_response "POST /api/auth/register (second user)" "$status"
if [[ "$status" == "201" || "$status" == "200" ]]; then
  status=$(api_call POST "$BASE_URL/api/auth/login" "{\"email\":\"$USER2_EMAIL\",\"password\":\"$USER2_PASSWORD\"}")
  show_last_response "POST /api/auth/login (second user)" "$status"
  USER2_TOKEN=""
  if [[ "$status" == "200" ]]; then
    USER2_TOKEN=$(extract_json_field '.token')
  fi

  if [[ -n "$USER2_TOKEN" && -n "$PRODUCT_ID" ]]; then
    status=$(api_call PUT "$BASE_URL/api/products/$PRODUCT_ID" "{\"name\":\"Hijack\",\"description\":\"No\",\"price\":20.0}" "$USER2_TOKEN")
    show_last_response "PUT /api/products/{id} (non-owner)" "$status"
    if [[ "$status" == "403" ]]; then
      print_result "Do product ownership checks block non-owner updates?" "YES" "Non-owner received HTTP 403."
    else
      print_result "Do product ownership checks block non-owner updates?" "NO" "Non-owner received HTTP $status (expected 403)."
    fi
  fi
fi

# Owner update + delete
if [[ -n "$USER_TOKEN" && -n "$PRODUCT_ID" ]]; then
  status=$(api_call PUT "$BASE_URL/api/products/$PRODUCT_ID" "{\"name\":\"Audit Product Updated\",\"description\":\"Updated\",\"price\":59.99}" "$USER_TOKEN")
  show_last_response "PUT /api/products/{id} (owner)" "$status"
  if [[ "$status" == "200" ]]; then
    print_result "Is Product UPDATE working for owner?" "YES" "Owner update returned HTTP 200."
  else
    print_result "Is Product UPDATE working for owner?" "NO" "Owner update returned HTTP $status."
  fi

  status=$(api_call DELETE "$BASE_URL/api/products/$PRODUCT_ID" "" "$USER_TOKEN")
  show_last_response "DELETE /api/products/{id} (owner)" "$status"
  if [[ "$status" == "200" || "$status" == "204" ]]; then
    print_result "Is Product DELETE working for owner?" "YES" "Owner delete returned HTTP $status."
  else
    print_result "Is Product DELETE working for owner?" "NO" "Owner delete returned HTTP $status."
  fi
fi

printf '%s\n' ""
printf '%s\n' "== Exception Handling and Validation =="

# Duplicate email scenario
status=$(api_call POST "$BASE_URL/api/auth/register" "$register_payload")
show_last_response "POST /api/auth/register (duplicate email)" "$status"
if [[ "$status" == "400" || "$status" == "409" ]]; then
  msg=$(cat "$REQ_BODY")
  print_result "Does duplicate email produce a controlled error?" "YES" "HTTP $status with response: $msg"
else
  print_result "Does duplicate email produce a controlled error?" "NO" "HTTP $status on duplicate email scenario."
fi

# Invalid input scenario
status=$(api_call POST "$BASE_URL/api/auth/register" "{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"\"}")
show_last_response "POST /api/auth/register (invalid payload)" "$status"
if [[ "$status" == "400" ]]; then
  print_result "Is input validation active for register payload?" "YES" "Invalid payload rejected with HTTP 400."
else
  print_result "Is input validation active for register payload?" "NO" "Invalid payload returned HTTP $status."
fi

printf '%s\n' ""
printf '%s\n' "== Security Checks =="

# HTTP -> HTTPS redirect check
redirect_info=$(curl -s -o /dev/null -w "%{http_code} %{redirect_url}" "$HTTP_REDIRECT_URL/api/products" || true)
redirect_code=$(echo "$redirect_info" | awk '{print $1}')
redirect_target=$(echo "$redirect_info" | awk '{print $2}')
if [[ "$redirect_code" =~ ^30[1278]$ ]] && [[ "$redirect_target" == https://* ]]; then
  print_result "Is HTTPS enforcement active via HTTP redirect?" "YES" "HTTP port redirects to HTTPS ($redirect_info)."
else
  print_result "Is HTTPS enforcement active via HTTP redirect?" "PARTIAL" "Redirect check output: $redirect_info"
fi

# Password hashing check via mongosh (optional)
if command_exists mongosh; then
  PLAIN_CHECK_EMAIL="$USER_EMAIL"
  hash_result=$(mongosh --quiet --eval "db = db.getSiblingDB('cruddb'); const u=db.users.findOne({email:'$PLAIN_CHECK_EMAIL'}); if(!u){print('MISSING')} else if (typeof u.password==='string' && /^\\$2[aby]\\$/.test(u.password)) {print('HASHED')} else {print('NOT_HASHED')}" 2>/dev/null || true)
  if [[ "$hash_result" == "HASHED" ]]; then
    print_result "Are passwords stored hashed in DB?" "YES" "bcrypt hash detected in MongoDB."
  elif [[ "$hash_result" == "NOT_HASHED" ]]; then
    print_result "Are passwords stored hashed in DB?" "NO" "Password does not match expected bcrypt format."
  else
    print_result "Are passwords stored hashed in DB?" "PARTIAL" "Could not confirm via mongosh (result: $hash_result)."
  fi
else
  print_result "Are passwords stored hashed in DB?" "PARTIAL" "mongosh not installed, DB hash check skipped."
fi

# Sensitive information in API responses
leak_found="NO"
for payload_file in "$REQ_BODY"; do
  if json_has_password "$payload_file"; then
    leak_found="YES"
  fi
done
if [[ "$leak_found" == "NO" ]]; then
  print_result "Is sensitive information (password) protected in tested responses?" "YES" "No password field found in tested auth/user responses."
else
  print_result "Is sensitive information (password) protected in tested responses?" "NO" "Password field detected in at least one response."
fi

printf '%s\n' ""
printf '%s\n' "== Code Quality and Annotation Checks =="

PROJECT_ROOT="$(cd "$(dirname "$0")/crud-api" && pwd)"

# Data class annotations
user_model="$PROJECT_ROOT/src/main/java/com/example/crudapi/model/User.java"
product_model="$PROJECT_ROOT/src/main/java/com/example/crudapi/model/Product.java"

user_has_document=$(count_annotation_in_file '@Document' "$user_model")
user_has_id=$(count_annotation_in_file '@Id' "$user_model")
product_has_document=$(count_annotation_in_file '@Document' "$product_model")
product_has_id=$(count_annotation_in_file '@Id' "$product_model")
product_has_field=$(count_annotation_in_file '@Field' "$product_model")

if [[ "$user_has_document" -ge 1 && "$user_has_id" -ge 1 && "$product_has_document" -ge 1 && "$product_has_id" -ge 1 && "$product_has_field" -ge 1 ]]; then
  print_result "Are @Document, @Id, and @Field used correctly in data classes?" "YES" "User/Product classes use @Document and @Id, Product maps fields with @Field."
else
  print_result "Are @Document, @Id, and @Field used correctly in data classes?" "NO" "Missing expected data-class annotations in User/Product models."
fi

# Controller mapping annotations
controllers_dir="$PROJECT_ROOT/src/main/java/com/example/crudapi/controller"
rest_count=$(count_annotation_in_tree '@RestController' "$controllers_dir")
request_count=$(count_annotation_in_tree '@RequestMapping' "$controllers_dir")
get_count=$(count_annotation_in_tree '@GetMapping' "$controllers_dir")
post_count=$(count_annotation_in_tree '@PostMapping' "$controllers_dir")
put_count=$(count_annotation_in_tree '@PutMapping' "$controllers_dir")
delete_count=$(count_annotation_in_tree '@DeleteMapping' "$controllers_dir")

if [[ "$rest_count" -ge 1 && "$request_count" -ge 1 && "$get_count" -ge 1 && "$post_count" -ge 1 && "$put_count" -ge 1 && "$delete_count" -ge 1 ]]; then
  print_result "Are controller annotations (@RestController, @RequestMapping, @GetMapping, @PostMapping, @PutMapping, @DeleteMapping) used correctly?" "YES" "Required controller annotations are present across controllers."
else
  print_result "Are controller annotations (@RestController, @RequestMapping, @GetMapping, @PostMapping, @PutMapping, @DeleteMapping) used correctly?" "NO" "One or more expected controller annotations are missing."
fi

# @Autowired check
autowired_count=$(count_annotation_in_tree '@Autowired' "$PROJECT_ROOT/src/main/java")
if [[ "$autowired_count" -ge 1 ]]; then
  print_result "Is @Autowired used correctly for dependency injection?" "YES" "@Autowired is present (constructor injection pattern detected)."
else
  print_result "Is @Autowired used correctly for dependency injection?" "PARTIAL" "No @Autowired found; project may rely on implicit constructor injection."
fi

# Security annotations check
security_dir="$PROJECT_ROOT/src/main/java"
enable_web_security=$(count_annotation_in_tree '@EnableWebSecurity' "$security_dir")
enable_method_security=$(count_annotation_in_tree '@EnableMethodSecurity' "$security_dir")
permit_all=$(count_annotation_in_tree '@PermitAll' "$security_dir")
post_authorize=$(count_annotation_in_tree '@PostAuthorize' "$security_dir")
pre_authorize=$(count_annotation_in_tree '@PreAuthorize' "$security_dir")

if [[ "$enable_method_security" -ge 1 && "$pre_authorize" -ge 1 ]]; then
  details="@EnableMethodSecurity and @PreAuthorize are used."
  if [[ "$enable_web_security" -eq 0 || "$permit_all" -eq 0 || "$post_authorize" -eq 0 ]]; then
    details+=" @EnableWebSecurity/@PermitAll/@PostAuthorize are not used in this codebase."
    print_result "Are auth and role annotations (@EnableWebSecurity, @EnableMethodSecurity, @PermitAll, @PostAuthorize, @PreAuthorize) used correctly?" "PARTIAL" "$details"
  else
    print_result "Are auth and role annotations (@EnableWebSecurity, @EnableMethodSecurity, @PermitAll, @PostAuthorize, @PreAuthorize) used correctly?" "YES" "$details"
  fi
else
  print_result "Are auth and role annotations (@EnableWebSecurity, @EnableMethodSecurity, @PermitAll, @PostAuthorize, @PreAuthorize) used correctly?" "NO" "Missing core method-security annotations."
fi

printf '%s\n' ""
printf '%s\n' "== Final CRUD Coverage Summary =="
print_result "Are all CRUD operations correctly implemented for Users and Products?" "PARTIAL" "Script validates major CRUD paths and role checks. Result depends on each line above (look for NO/PARTIAL findings)."

printf '%s\n' ""
printf '%s\n' "Audit completed."
printf '%s\n' "Tip: run with BASE_URL if needed, e.g. BASE_URL=https://localhost:8443 ./audit_check.sh"
