UPDATE openclaw_gateway_node
SET config_path = CASE
  WHEN LOCATE('/', REVERSE(config_path)) > 0 THEN
    CONCAT(SUBSTRING(config_path, 1, LENGTH(config_path) - LOCATE('/', REVERSE(config_path)) + 1), 'jeecg-agents.json')
  ELSE 'jeecg-agents.json'
END
WHERE config_path LIKE '%openclaw.json';
