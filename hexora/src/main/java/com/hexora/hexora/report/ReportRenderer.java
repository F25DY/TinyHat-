package com.hexora.hexora.report;

/**
 * Turns a {@link ReportSnapshot} into a downloadable file. One implementation per output family
 * (PDF, XLSX). Implementations are registered as Spring beans and picked by {@link #supports}.
 *
 * <p>Contract for implementations:
 * <ul>
 *   <li>Read only the snapshot; never query live tables.</li>
 *   <li>Escape every text field for the target format; the snapshot holds raw user and NVD text.</li>
 *   <li>PDF renderers must resolve resources from the classpath only and never fetch remote URLs.</li>
 *   <li>Never derive the file name from snapshot content: {@link ReportType#fileName} is used.</li>
 * </ul>
 */
public interface ReportRenderer {

    boolean supports(ReportType type);

    byte[] render(ReportSnapshot snapshot);
}
