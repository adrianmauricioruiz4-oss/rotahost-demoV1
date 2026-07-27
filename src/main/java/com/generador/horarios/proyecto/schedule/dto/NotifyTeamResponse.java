package com.generador.horarios.proyecto.schedule.dto;

import java.util.List;

/**
 * Resultado de avisar al equipo.
 *
 * @param mailEnabled false si el envío de correo no está configurado: en ese caso no ha
 *                    salido nada y el aviso solo ha quedado en el log del servidor
 * @param sent        a cuántas personas se ha escrito
 * @param skipped     nombres a los que no se ha podido escribir, por no tener correo o por
 *                    haber fallado el envío
 */
public record NotifyTeamResponse(boolean mailEnabled, int sent, List<String> skipped) {
}
