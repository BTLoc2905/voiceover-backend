-- The 2..10 limit is a publication rule. Drafts may contain more stops.
ALTER TABLE route_stop DROP CHECK chk_route_stop_order;
ALTER TABLE route_stop ADD CONSTRAINT chk_route_stop_order CHECK (stop_order >= 1);
