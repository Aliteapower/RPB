import QRCode from 'qrcode';
import jsQR from 'jsqr';
import { layoutCenterName, wrapName } from './nameLayout';
/** QR generation is entirely local. Validate pixels after adding the name, before enabling distribution. */
export async function renderTenantEntryQr(value: string, tenantName: string, caption: string) {
    if (!value.trim() || !tenantName.trim())
        throw new Error('entry_qr_required');
    const size = 1024, qr = document.createElement('canvas');
    await QRCode.toCanvas(qr, value, { width: size, margin: 4, errorCorrectionLevel: 'H', color: { dark: '#142433', light: '#ffffff' } });
    const context = qr.getContext('2d', { willReadFrequently: true });
    if (!context)
        throw new Error('entry_qr_canvas');
    const original = context.getImageData(0, 0, size, size);
    const measure = (text: string, fontSize: number) => { context.font = `600 ${fontSize}px "Microsoft YaHei", "Noto Sans SC", sans-serif`; return context.measureText(text).width; };
    let validated = false;
    for (const ratio of [0.26, 0.23, 0.20]) {
        context.putImageData(original, 0, 0);
        const width = Math.floor(size * ratio), height = 132, x = (size - width) / 2, y = (size - height) / 2;
        context.fillStyle = '#ffffff';
        context.fillRect(x, y, width, height);
        const layout = layoutCenterName(tenantName, measure, width - 20, height - 16), lineHeight = layout.fontSize * 1.2;
        context.font = `600 ${layout.fontSize}px "Microsoft YaHei", "Noto Sans SC", sans-serif`;
        context.textAlign = 'center';
        context.textBaseline = 'middle';
        context.fillStyle = '#142433';
        layout.lines.forEach((line, index) => context.fillText(line, size / 2, size / 2 + (index - (layout.lines.length - 1) / 2) * lineHeight));
        const pixels = context.getImageData(0, 0, size, size);
        if (jsQR(pixels.data, size, size, { inversionAttempts: 'dontInvert' })?.data === value) {
            validated = true;
            break;
        }
    }
    if (!validated)
        throw new Error('entry_qr_unreadable');
    // Keep the complete tenant name outside the protected center, including in the exported PNG.
    const captions = wrapName(caption, text => measure(text, 30), size - 96);
    const urls = wrapName(value, text => measure(text, 22), size - 96);
    for (const nameSize of [38, 32, 28, 24]) {
        const fullName = wrapName(tenantName, text => measure(text, nameSize), size - 96);
        const image = composeEntryQr(qr, fullName, nameSize, captions, urls);
        const pixels = image.getContext('2d')!.getImageData(0, 0, image.width, image.height);
        // Header text can confuse a scanner even when the QR alone is valid.
        // Validate the actual exported image, including its full tenant name and URL.
        if (jsQR(pixels.data, image.width, image.height, { inversionAttempts: 'dontInvert' })?.data === value) {
            return image.toDataURL('image/png');
        }
    }
    throw new Error('entry_qr_unreadable');
}

function composeEntryQr(qr: HTMLCanvasElement, fullName: string[], nameSize: number, captions: string[], urls: string[]) {
    const size = qr.width;
    const nameLineHeight = Math.ceil(nameSize * 1.26);
    const top = 48 + fullName.length * nameLineHeight + captions.length * 40;
    const bottom = urls.length * 30 + 40;
    const image = document.createElement('canvas');
    image.width = size;
    image.height = top + size + bottom;
    const out = image.getContext('2d');
    if (!out)
        throw new Error('entry_qr_canvas');
    out.fillStyle = '#ffffff';
    out.fillRect(0, 0, image.width, image.height);
    out.fillStyle = '#142433';
    out.textAlign = 'center';
    out.textBaseline = 'middle';
    out.font = `600 ${nameSize}px "Microsoft YaHei", "Noto Sans SC", sans-serif`;
    fullName.forEach((line, i) => out.fillText(line, size / 2, 38 + i * nameLineHeight));
    out.font = '30px "Microsoft YaHei", "Noto Sans SC", sans-serif';
    captions.forEach((line, i) => out.fillText(line, size / 2, 48 + fullName.length * nameLineHeight + i * 40));
    out.drawImage(qr, 0, top);
    out.font = '22px "Microsoft YaHei", "Noto Sans SC", sans-serif';
    urls.forEach((line, i) => out.fillText(line, size / 2, top + size + 16 + i * 30));
    return image;
}
