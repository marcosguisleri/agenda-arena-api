-- A V18 criou a FK matches.schedule_slot_id sem índice (só organizer_id e a origem da ocorrência
-- têm). Sem ele, excluir ou atualizar um horário (schedule_slots) e qualquer consulta de partidas
-- por horário varrem a tabela inteira de partidas, que só cresce.

CREATE INDEX idx_matches_schedule_slot
    ON matches (schedule_slot_id);
