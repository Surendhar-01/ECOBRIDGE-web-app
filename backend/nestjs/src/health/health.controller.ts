import { Controller, Get } from '@nestjs/common';

/**
 * Liveness probe for the platform health check (Render, Docker, uptime monitors).
 *
 * Deliberately dependency-free: it must answer 200 even when Supabase or the
 * FastAPI voice service is unreachable, otherwise a transient outage of a
 * downstream would cause the platform to kill and restart this service.
 */
@Controller('api')
export class HealthController {
  @Get('health')
  health() {
    return {
      status: 'ok',
      service: 'ECOBRIDGES NestJS API',
      fastApiVoiceUrl:
        (process.env.FASTAPI_VOICE_URL || 'http://localhost:8000').replace(/\/+$/, ''),
      timestamp: new Date().toISOString(),
    };
  }
}
