import { Transform, Type } from 'class-transformer';
import {
  IsBoolean,
  IsIn,
  IsNotEmpty,
  IsOptional,
  IsString,
  MaxLength,
  ValidateNested,
} from 'class-validator';
import { RoleType } from '../../auth/dto/auth.dto';

export enum ActiveLanguage {
  EN = 'en',
  HI = 'hi',
  MR = 'mr',
}

/**
 * The rest of the platform identifies a role by its lowercase database slug
 * (`informal_collector`), and the Android `RoleType` enum uses those same ids.
 * `RoleType` in auth.dto.ts is uppercase, so a raw value coming from the app or
 * from `profiles.role` would fail `@IsIn` and 400. Normalising here keeps the
 * downstream identity comparisons in the router correct without having to
 * change the shared auth enum.
 */
const NormalizeRole = () =>
  Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.toUpperCase() : value,
  );

/**
 * Where the user currently is, so the router can resolve a context-aware
 * destination and tell an OPEN_LOGIN apart from a NAVIGATE_DASHBOARD.
 */
export class NavigationContextDto {
  @IsNotEmpty()
  @IsString()
  @MaxLength(64)
  currentScreen: string;

  @IsOptional()
  @NormalizeRole()
  @IsIn(Object.values(RoleType), {
    message: `currentRole must be one of: ${Object.values(RoleType).join(', ')}`,
  })
  currentRole?: RoleType;

  @IsOptional()
  @IsBoolean()
  isAuthenticated?: boolean;

  @IsOptional()
  @IsIn(Object.values(ActiveLanguage), { message: 'activeLanguage must be one of: en, hi, mr' })
  activeLanguage?: ActiveLanguage;
}

/**
 * Body of POST /api/voice/intent.
 *
 * Every property MUST carry a class-validator decorator. `main.ts` installs a
 * global `ValidationPipe({ whitelist: true })`, and `whitelist` *deletes* any
 * property that has no decorator. With an undecorated DTO the body arrives as
 * `{}`, so `dto.transcript` is `undefined` and every request 500s on
 * `transcript.trim()`. The Android client in `BackendVoiceClient.kt` sends
 * exactly this shape.
 */
export class ProcessVoiceDto {
  @IsNotEmpty()
  @IsString()
  @MaxLength(1000, { message: 'transcript must be 1000 characters or fewer' })
  transcript: string;

  @IsOptional()
  @ValidateNested()
  @Type(() => NavigationContextDto)
  context?: NavigationContextDto;

  @IsOptional()
  @NormalizeRole()
  @IsIn(Object.values(RoleType), {
    message: `userRole must be one of: ${Object.values(RoleType).join(', ')}`,
  })
  userRole?: RoleType;
}
