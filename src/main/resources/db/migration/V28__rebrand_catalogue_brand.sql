-- The storefront was renamed from Plain Thread to Nexus Commerce. V21 keeps its original
-- content and filename because Flyway validates the checksum of every applied migration;
-- this brings existing databases in line with the name fresh installs should show.
UPDATE products SET brand = 'Nexus Commerce' WHERE brand = 'Plain Thread';
