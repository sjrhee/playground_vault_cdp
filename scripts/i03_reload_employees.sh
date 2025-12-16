#!/bin/bash
set -e

# Define variables
CONTAINER_NAME="demo_mysql"
MYSQL_USER="root"
MYSQL_PASSWORD="rootpassword"
MYSQL_DATABASE="mysql_employees"
IMPORT_PATH_IN_CONTAINER="/docker-entrypoint-initdb.d/e01_employee_export.csv"
LOCAL_INPUT_FILE="./sample_data/e01_employee_export.csv"

echo "Step 1: Checking for input file..."
if [ ! -f "$LOCAL_INPUT_FILE" ]; then
    echo "Error: Input file '$LOCAL_INPUT_FILE' not found."
    echo "Please ensure you have generated the file (e.g., using i02_convert.sh) and named it correctly."
    exit 1
fi

echo "Step 2: Copying input file to container..."
docker cp $LOCAL_INPUT_FILE $CONTAINER_NAME:$IMPORT_PATH_IN_CONTAINER
# Fix permissions so MySQL user can read it
docker exec $CONTAINER_NAME chown mysql:mysql $IMPORT_PATH_IN_CONTAINER

echo "Step 3: Executing Reload SQL..."

# Execute SQL using a Here-Doc
docker exec -i $CONTAINER_NAME mysql -u$MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE <<EOF
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE employee;

LOAD DATA INFILE '$IMPORT_PATH_IN_CONTAINER'
INTO TABLE employee
FIELDS TERMINATED BY ','
LINES TERMINATED BY '\n'
(emp_no, employee_id, date_of_birth, first_name, last_name, middle_names, gender, date_of_hiring, date_of_termination, date_of_probation_end, ssn_no);
SET FOREIGN_KEY_CHECKS = 1;
EOF

echo "Success! Data reloaded from $LOCAL_INPUT_FILE"
