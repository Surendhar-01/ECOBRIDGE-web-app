import { Module } from '@nestjs/common';
import { AuthModule } from './auth/auth.module';
import { VoiceModule } from './voice/voice.module';
import { HealthController } from './health/health.controller';

@Module({
  imports: [AuthModule, VoiceModule],
  controllers: [HealthController],
})
export class AppModule {}
