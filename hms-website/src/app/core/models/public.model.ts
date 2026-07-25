export type ThemeMode = 'LIGHT' | 'DARK' | 'CUSTOM' | 'AUTO';
export type CornerRadiusStyle = 'SQUARE' | 'ROUNDED' | 'PILL';

export interface PublicConfig {
  name: string;
  logoUrl: string | null;
  faviconUrl: string | null;
  websiteEnabled: boolean;
  domain: string | null;
  themePrimaryColor: string | null;
  themeSecondaryColor: string | null;
  seoDefaultTitle: string | null;
  seoDefaultDescription: string | null;
  socialFacebookUrl: string | null;
  socialInstagramUrl: string | null;
  socialYoutubeUrl: string | null;
  whatsappNumber: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  themeMode: ThemeMode;
  themeTertiaryColor: string | null;
  fontFamily: string | null;
  cornerRadiusStyle: CornerRadiusStyle;
  headerBackgroundColor: string | null;
  footerBackgroundColor: string | null;
  footerText: string | null;
}

export interface PublicDepartment {
  id: number;
  name: string;
}

export interface PublicConsultant {
  id: number;
  name: string;
  departmentId: number;
  departmentName: string;
  specializationName: string | null;
  profile: string | null;
  consultationFee: number;
  imageUrl: string | null;
}

export interface PublicSiteContent {
  aboutUsBody: string | null;
  missionBody: string | null;
  visionBody: string | null;
  homeIntroTitle: string | null;
  homeIntroBody: string | null;
}

export interface PublicBannerSlide {
  id: number;
  title: string;
  subtitle: string | null;
  imageUrl: string | null;
  linkUrl: string | null;
  sortOrder: number | null;
}

export type GalleryItemType = 'PHOTO' | 'VIDEO';

export interface PublicGalleryItem {
  id: number;
  type: GalleryItemType;
  title: string | null;
  mediaUrl: string | null;
  album: string | null;
}

export interface PublicNewsEvent {
  id: number;
  title: string;
  body: string | null;
  eventDate: string | null;
  imageUrl: string | null;
  publishedAt: string | null;
}

export interface PublicTestimonial {
  id: number;
  patientName: string;
  quote: string;
  rating: number | null;
  imageUrl: string | null;
}

export interface PublicFaq {
  id: number;
  question: string;
  answer: string;
  sortOrder: number | null;
}

export interface PublicHealthPackage {
  id: number;
  name: string;
  description: string | null;
  price: number;
  includes: string | null;
}

export interface PublicBlogPost {
  id: number;
  title: string;
  slug: string;
  body: string | null;
  imageUrl: string | null;
  publishedAt: string | null;
}

export interface PublicCareerOpening {
  id: number;
  title: string;
  departmentId: number | null;
  departmentName: string | null;
  description: string | null;
  applyEmail: string | null;
}

export type AppointmentSession = 'MORNING' | 'AFTERNOON' | 'EVENING' | 'NIGHT';
export type SlotStatus = 'AVAILABLE' | 'PAST' | 'BOOKED';

/** Deliberately has no patient fields - the backend strips who booked a slot before this ever reaches an anonymous visitor. */
export interface PublicSlot {
  date: string;
  time: string;
  session: AppointmentSession;
  status: SlotStatus;
}

/** Deliberately minimal - "knows the mobile number" is a weak proof of identity, so this never carries mobile/email/address/DOB. */
export interface PatientLookupResult {
  patientId: number;
  name: string;
  age: number | null;
  gender: string | null;
}

export interface NewPatientDetails {
  firstName: string;
  lastName?: string | null;
  dateOfBirth?: string | null;
  gender?: string | null;
  age?: number | null;
  email?: string | null;
  address?: string | null;
}

/** Either existingPatientId or newPatient must be set, never both - see PublicAppointmentService.book() on the backend. */
export interface PublicBookingRequest {
  mobileNumber: string;
  existingPatientId?: number | null;
  newPatient?: NewPatientDetails | null;
  consultantId: number;
  date: string;
  slotTime: string;
}

export interface PublicBookingResult {
  appointmentId: number;
  patientName: string;
  consultantName: string;
  date: string;
  slotTime: string;
}
