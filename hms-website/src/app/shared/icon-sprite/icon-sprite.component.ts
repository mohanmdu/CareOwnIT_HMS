import { Component } from '@angular/core';

/**
 * Single source of truth for every icon used across the public site - mounted
 * once in app.component.html (display:none, purely a <symbol> definition
 * block) and referenced everywhere via IconComponent's <use href="#i-name">.
 * Hand-authored simple stroke icons (no icon library dependency existed
 * before this - see the redesign audit) rather than long decorative path data.
 */
@Component({
  selector: 'app-icon-sprite',
  standalone: true,
  template: `
    <svg width="0" height="0" style="position:absolute" aria-hidden="true">
      <defs>
        <symbol id="i-heart" viewBox="0 0 24 24"
          ><path
            d="M12 20.5s-7.5-4.6-10-9.2C.4 8 2 4.5 5.5 4c2-.3 3.9.6 5 2.2 1.1-1.6 3-2.5 5-2.2 3.5.5 5.1 4 3.5 7.3-2.5 4.6-10 9.2-10 9.2Z"
        /></symbol>
        <symbol id="i-heart-pulse" viewBox="0 0 24 24"
          ><path
            d="M12 20.5s-7.5-4.6-10-9.2C.4 8 2 4.5 5.5 4c2-.3 3.9.6 5 2.2 1.1-1.6 3-2.5 5-2.2 3.5.5 5.1 4 3.5 7.3-2.5 4.6-10 9.2-10 9.2Z"
          /><path d="M4 11.5h2.5l1.3-2.6 1.7 4.3 1.3-2.9.9 1.7h6.3" /></symbol>
        <symbol id="i-dermatology" viewBox="0 0 24 24"
          ><path
            d="M8.5 21c-1.2-.2-3.8-1.4-3.8-6 0-1 .3-1.7.8-2.3C5.2 12 5 11 5 10c0-3.9 3.1-7 7-7 3.3 0 6.1 2.2 6.8 5.2.9.3 1.5 1.2 1.5 2.1 0 1-.6 1.8-1.5 2.2-.2 2.1-1.2 3.9-2.6 5.1"
          /><circle cx="9.5" cy="9.5" r=".55" fill="currentColor" stroke="none" /><circle
            cx="12.7"
            cy="7.8"
            r=".55"
            fill="currentColor"
            stroke="none"
          /><circle cx="10.8" cy="12.6" r=".55" fill="currentColor" stroke="none" /></symbol>
        <symbol id="i-ent" viewBox="0 0 24 24"
          ><path
            d="M13.5 3.5c-3.6 0-6.3 2.9-6.3 6.4 0 1.7.6 2.5 1.1 3.5.5.9.9 1.7.9 3.1a3.4 3.4 0 0 0 3.4 3.4c1.9 0 3.3-1.4 3.3-3.2 0-1-.4-1.6-.9-2.2-.5-.7-1-1.4-1-2.7 0-1.4 1.1-2.1 1.1-3.6a2.5 2.5 0 0 0-1.9-2.4"
          /><path d="M9.8 12.2c-.3.9-.3 1.9.3 2.7" /></symbol>
        <symbol id="i-gynecology" viewBox="0 0 24 24"
          ><path d="M12 13.2v6.3M9.3 19.5h5.4" /><path
            d="M12 13.2c-2 0-3.6-1.5-3.6-3.5S9.3 5.8 10.3 4.8c.5 3 1.4 3.9 1.7 4"
          /><path d="M12 13.2c2 0 3.6-1.5 3.6-3.5S14.7 5.8 13.7 4.8c-.5 3-1.4 3.9-1.7 4" /></symbol>
        <symbol id="i-brain" viewBox="0 0 24 24"
          ><path
            d="M9 3.5c-2 0-3.5 1.6-3.5 3.4 0 .5.1 1 .3 1.4C4.5 8.8 3.5 10.2 3.5 12c0 1.6.8 2.9 2 3.7-.2.5-.3 1-.3 1.6 0 2 1.7 3.7 3.8 3.7.6 0 1.1-.1 1.6-.4.4.9 1.3 1.4 2.4 1.4V6.3C13 4.6 11.3 3.5 9 3.5Z"
          /><path
            d="M15 3.5c2 0 3.5 1.6 3.5 3.4 0 .5-.1 1-.3 1.4 1.3.5 2.3 1.9 2.3 3.7 0 1.6-.8 2.9-2 3.7.2.5.3 1 .3 1.6 0 2-1.7 3.7-3.8 3.7-.6 0-1.1-.1-1.6-.4-.4.9-1.3 1.4-2.4 1.4V6.3c1-1.7 2.7-2.8 4-2.8Z"
        /></symbol>
        <symbol id="i-bone" viewBox="0 0 24 24"
          ><path
            d="M4.9 14.4a2.6 2.6 0 1 0-3.6 3.6l4.7 4.7a2.6 2.6 0 1 0 3.6-3.6Zm14.2-14.2a2.6 2.6 0 1 0-3.6 3.6l-9.8 9.8a2.6 2.6 0 1 0 3.6 3.6l9.8-9.8a2.6 2.6 0 1 0 3.6-3.6Z"
        /></symbol>
        <symbol id="i-baby" viewBox="0 0 24 24"
          ><circle cx="12" cy="7" r="4" /><path d="M5 21c0-4 3-6.5 7-6.5s7 2.5 7 6.5" /><path
            d="M9.5 6.5c.5.7 1.4.7 2 0M12.5 6.5c.5.7 1.4.7 2 0"
        /></symbol>
        <symbol id="i-tooth" viewBox="0 0 24 24"
          ><path
            d="M12 3c-2 0-2.6 1.4-4.2 1.4S4.8 3 3.6 4.2C2.4 5.4 2 8 3 11c.7 2.2 1.6 3 2 6 .3 2.4 1 4.5 2.4 4.5 1.6 0 1.6-3.6 2.3-5.6.3-.8.7-1.4 2.3-1.4s2 .6 2.3 1.4c.7 2 .7 5.6 2.3 5.6 1.4 0 2.1-2.1 2.4-4.5.4-3 1.3-3.8 2-6 1-3 .6-5.6-.6-6.8-1.2-1.2-2.9.2-4.4.2S14 3 12 3Z"
        /></symbol>
        <symbol id="i-eye" viewBox="0 0 24 24"
          ><path d="M2 12s3.8-6.5 10-6.5S22 12 22 12s-3.8 6.5-10 6.5S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /></symbol>
        <symbol id="i-stetho" viewBox="0 0 24 24"
          ><path d="M6 3v6.5a4.5 4.5 0 0 0 9 0V3" /><path d="M6 3H4.5M15 3h1.5" /><circle cx="19" cy="15" r="2.5" /><path
            d="M15 9.5v2A6.5 6.5 0 0 1 8.5 18H8a4 4 0 0 0 0 8"
        /></symbol>
        <symbol id="i-shield" viewBox="0 0 24 24"
          ><path d="M12 3 4.5 6v6c0 5 3.4 8.4 7.5 9.9 4.1-1.5 7.5-4.9 7.5-9.9V6Z" /><path d="m8.5 12 2.5 2.5L16 9" /></symbol>
        <symbol id="i-ambulance" viewBox="0 0 24 24"
          ><path d="M3 16V7a1 1 0 0 1 1-1h9v10" /><path d="M13 10h4l3 3v3h-7z" /><circle cx="7" cy="18.5" r="1.8" /><circle
            cx="17.5"
            cy="18.5"
            r="1.8"
          /><path d="M6.5 8.5v3M5 10h3" /></symbol>
        <symbol id="i-calendar" viewBox="0 0 24 24"
          ><rect x="3.5" y="5" width="17" height="16" rx="2.5" /><path d="M8 3v4M16 3v4M3.5 10h17" /></symbol>
        <symbol id="i-phone" viewBox="0 0 24 24"
          ><path
            d="M5.5 4h3l1.5 4.5-2 1.5a12 12 0 0 0 6 6l1.5-2 4.5 1.5v3a2 2 0 0 1-2.2 2C9.6 20 4 14.4 3.5 6.2A2 2 0 0 1 5.5 4Z"
        /></symbol>
        <symbol id="i-mail" viewBox="0 0 24 24"><rect x="3" y="5.5" width="18" height="13" rx="2" /><path d="m4 6.5 8 6 8-6" /></symbol>
        <symbol id="i-pin" viewBox="0 0 24 24"
          ><path d="M12 21s7-6.2 7-11.5A7 7 0 0 0 5 9.5C5 14.8 12 21 12 21Z" /><circle cx="12" cy="9.5" r="2.4" /></symbol>
        <symbol id="i-clock" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3.5 2" /></symbol>
        <symbol id="i-star" viewBox="0 0 24 24"
          ><path
            fill="currentColor"
            stroke="none"
            d="m12 2.5 3 6.2 6.8.9-4.9 4.7 1.2 6.7L12 17.8l-6.1 3.2 1.2-6.7-4.9-4.7 6.8-.9Z"
        /></symbol>
        <symbol id="i-arrow" viewBox="0 0 24 24"><path d="M4 12h16M13 5l7 7-7 7" /></symbol>
        <symbol id="i-check" viewBox="0 0 24 24"><path d="m4 12.5 5 5L20 6.5" /></symbol>
        <symbol id="i-award" viewBox="0 0 24 24"><circle cx="12" cy="8.5" r="5.5" /><path d="M8.5 13.2 7 21l5-2.5 5 2.5-1.5-7.8" /></symbol>
        <symbol id="i-users" viewBox="0 0 24 24"
          ><circle cx="9" cy="8" r="3.2" /><path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6" /><circle cx="17" cy="8.5" r="2.5" /><path
            d="M16 14.3c2.6.3 4.5 2.3 4.5 5.2"
        /></symbol>
        <symbol id="i-building" viewBox="0 0 24 24"
          ><path d="M5 21V5a1 1 0 0 1 1-1h4v17M14 21V9a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v12" /><path
            d="M8 8h1M8 12h1M8 16h1M17 12h1M17 16h1"
        /></symbol>
        <symbol id="i-menu" viewBox="0 0 24 24"><path d="M4 7h16M4 12h16M4 17h16" /></symbol>
        <symbol id="i-close" viewBox="0 0 24 24"><path d="m5 5 14 14M19 5 5 19" /></symbol>
        <symbol id="i-fb" viewBox="0 0 24 24"
          ><path d="M14 21v-7h2.4l.4-3H14V9c0-.9.2-1.5 1.6-1.5H17V5c-.3 0-1.3-.1-2.3-.1-2.3 0-3.7 1.4-3.7 4v2H8.5v3H11v7Z" /></symbol>
        <symbol id="i-ig" viewBox="0 0 24 24"><rect x="3.5" y="3.5" width="17" height="17" rx="5" /><circle cx="12" cy="12" r="4" /><circle
          cx="17.2"
          cy="6.8"
          r="1"
        /></symbol>
        <symbol id="i-yt" viewBox="0 0 24 24"
          ><rect x="2.5" y="6" width="19" height="12" rx="3.5" /><path fill="currentColor" stroke="none" d="M10.5 9.5v5l4.5-2.5Z" /></symbol>
        <symbol id="i-whatsapp" viewBox="0 0 24 24"
          ><path d="M12 21a9 9 0 1 0-7.8-4.5L3 21l4.7-1.2A9 9 0 0 0 12 21Z" /><path
            d="M8.7 8.4c.2-.4.4-.4.6-.4h.5c.2 0 .3.1.5.4.2.4.6 1.3.6 1.4.1.1.1.2 0 .4-.1.1-.1.2-.2.3-.1.2-.2.3-.3.4-.2.2-.3.3-.1.6.1.3.7 1.1 1.5 1.8 1 .9 1.8 1.2 2.1 1.3.2.1.4.1.5-.1.1-.2.6-.6.7-.8.2-.2.3-.2.5-.1l1.3.6c.2.1.3.1.3.3.1.1.1.7-.2 1.3-.2.6-1.3 1.1-1.7 1.2-.4.1-1 .1-1.6-.1-.4-.1-.8-.2-1.4-.5-2.4-1-4-3.5-4.1-3.7-.1-.1-1-1.3-1-2.5 0-1.2.6-1.8.8-2Z"
        /></symbol>
      </defs>
    </svg>
  `
})
export class IconSpriteComponent {}
