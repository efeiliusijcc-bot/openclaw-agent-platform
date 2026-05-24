UPDATE openclaw_gateway_node
SET config_path = CASE
  WHEN position('/' in reverse(config_path)) > 0 THEN
    substring(config_path from 1 for length(config_path) - position('/' in reverse(config_path)) + 1) || 'jeecg-agents.json'
  ELSE 'jeecg-agents.json'
END
WHERE config_path LIKE '%openclaw.json';
