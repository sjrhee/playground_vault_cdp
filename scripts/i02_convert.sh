set -e

crdp-file-converter sample_data/employee_export.csv -e -c10 --host 192.168.100.11 --port 32082 -p3 \
    --policy dev-policy-01 --user dev-user01
sleep 1
mv e01_employee_export.csv sample_data/
