import { Body, Controller, HttpCode, HttpStatus, Post } from '@nestjs/common';
import { VoiceIntentRouterService, NavigationContext } from './voice-intent-router.service';
import { ProcessVoiceDto } from './dto/process-voice.dto';

@Controller('api/voice')
export class VoiceController {
  constructor(private readonly voiceIntentRouter: VoiceIntentRouterService) {}

  /**
   * The DTO is validated by the global `ValidationPipe({ whitelist: true })`,
   * so it must come from `./dto/process-voice.dto` where every property is
   * decorated. An inline undecorated class is silently stripped to `{}`, which
   * made every request 500 on `transcript.trim()`.
   *
   * The shape below is what `BackendVoiceClient.kt` posts from the Android app.
   */
  @Post('intent')
  @HttpCode(HttpStatus.OK)
  async processVoiceIntent(@Body() dto: ProcessVoiceDto) {
    const context: NavigationContext = {
      currentScreen: dto.context?.currentScreen ?? 'INTRO',
      currentRole: dto.context?.currentRole ?? dto.userRole,
      isAuthenticated: dto.context?.isAuthenticated ?? dto.userRole !== undefined,
      activeLanguage: dto.context?.activeLanguage ?? 'en',
    };
    return this.voiceIntentRouter.routeVoiceIntent(dto.transcript, context, dto.userRole);
  }
}
