export function wrapName(text: string, measure: (text: string) => number, width: number): string[] {
    const lines: string[] = [];
    let line = '';
    for (const character of Array.from(text.trim().replace(/\s+/gu, ' '))) {
        if (line && measure(line + character) > width) {
            lines.push(line.trim());
            line = character.trimStart();
        }
        else
            line += character;
    }
    if (line)
        lines.push(line.trim());
    return lines;
}
export function layoutCenterName(text: string, measure: (text: string, fontSize: number) => number, width: number, height: number) {
    for (let fontSize = 42; fontSize >= 22; fontSize -= 2) {
        const lines = wrapName(text, value => measure(value, fontSize), width);
        if (lines.length <= 3 && lines.length * fontSize * 1.2 <= height)
            return { lines, fontSize, truncated: false };
    }
    const fontSize = 22, maxLines = Math.max(1, Math.min(3, Math.floor(height / (fontSize * 1.2))));
    const all = wrapName(text, value => measure(value, fontSize), width), lines = all.slice(0, maxLines);
    if (all.length > maxLines) {
        let last = Array.from(lines.at(-1) ?? '');
        while (last.length && measure(last.join('') + '…', fontSize) > width)
            last.pop();
        lines[lines.length - 1] = last.join('') + '…';
    }
    return { lines, fontSize, truncated: all.length > maxLines };
}
