package br.dev.guisleri.agendaarena.sportfield.controller;

import br.dev.guisleri.agendaarena.sportfield.dto.CreateSportFieldRequestDTO;
import br.dev.guisleri.agendaarena.sportfield.dto.SportFieldResponseDTO;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.service.SportFieldService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/establishments/{establishmentId}/sports-fields")
public class SportFieldController {

    private final SportFieldService sportFieldService;

    public SportFieldController(SportFieldService sportFieldService) {
        this.sportFieldService = sportFieldService;
    }

    @PostMapping
    public ResponseEntity<SportFieldResponseDTO> createSportField(
            @PathVariable Long establishmentId,
            @Valid @RequestBody CreateSportFieldRequestDTO requestDTO
    ) {

        SportField sportField = sportFieldService.createSportField(
                establishmentId,
                requestDTO.name(),
                requestDTO.sportType()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SportFieldResponseDTO.fromEntity(sportField));
    }

    @GetMapping
    public ResponseEntity<List<SportFieldResponseDTO>> findAllSportFields(
            @PathVariable Long establishmentId
    ) {
        List<SportField> sportFields =
                sportFieldService.findAllSportFields(establishmentId);

        return ResponseEntity.ok(
                sportFields.stream()
                        .map(SportFieldResponseDTO::fromEntity)
                        .toList()
        );
    }

    @GetMapping("/{sportFieldId}")
    public ResponseEntity<SportFieldResponseDTO> findSportFieldById(
            @PathVariable Long establishmentId,
            @PathVariable Long sportFieldId
    ) {
        SportField sportField = sportFieldService.findSportFieldById(establishmentId, sportFieldId);

        return ResponseEntity.ok(
                SportFieldResponseDTO.fromEntity(sportField)
        );
    }

}
