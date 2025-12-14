#!/bin/bash
set -e

echo "Step 2: Prepare Data and SQL Scripts"

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SAMPLE_DATA_DIR="$BASE_DIR/scripts/sample_data"

# Ensure sample_data exists (it should be copied already or exist in structure)
if [ ! -d "$SAMPLE_DATA_DIR" ]; then
    echo "Error: sample_data directory not found at $SAMPLE_DATA_DIR"
    exit 1
fi

# 1. Prepare 01_schema.sql 
if [ -f "$SAMPLE_DATA_DIR/schema.sql" ]; then
    echo "Found schema.sql, creating 01_schema.sql..."
    echo "USE mysql_employees;" > "$SAMPLE_DATA_DIR/01_schema.sql"
    cat "$SAMPLE_DATA_DIR/schema.sql" >> "$SAMPLE_DATA_DIR/01_schema.sql"
    rm "$SAMPLE_DATA_DIR/schema.sql"
elif [ -f "$SAMPLE_DATA_DIR/01_schema.sql" ]; then
     echo "01_schema.sql already exists. Skipping creation."
else
    echo "Error: schema.sql (or 01_schema.sql) not found in sample_data."
    # Allowing continue if 01_schema.sql exists could be better, but strict check for now.
    # If users re-run, original schema.sql is gone. 
     if [ ! -f "$SAMPLE_DATA_DIR/01_schema.sql" ]; then
        exit 1
     fi
fi

# 2. Create 02_load_data.sql for CSV loading
echo "Creating 02_load_data.sql..."
cat > "$SAMPLE_DATA_DIR/02_load_data.sql" <<EOF
USE mysql_employees;
SET GLOBAL local_infile=1;
LOAD DATA INFILE '/docker-entrypoint-initdb.d/employee.csv' INTO TABLE employee FIELDS TERMINATED BY ',' LINES TERMINATED BY '\n' IGNORE 1 LINES;

EOF

echo "Data preparation complete. SQL scripts created in $SAMPLE_DATA_DIR."
