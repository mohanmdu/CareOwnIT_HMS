DELETE FROM role_module_permission WHERE module_key = 'BILLING';
DROP TABLE IF EXISTS invoice_line_item;
DROP TABLE IF EXISTS invoice;
DROP TABLE IF EXISTS billing_item;
DROP TABLE IF EXISTS billing_category;
