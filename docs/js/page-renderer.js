/**
 * Renders Drawboard pages using a layered approach:
 * - Layer 0 (bottom): HTML elements for text (preserves formatting) and images
 * - Layer 1 (top): Canvas for freehand drawings
 */
class PageRenderer {
    constructor(container) {
        this.container = container;
        this.currentPage = null;
        this.scale = 1.0;
        this.images = new Map();

        // Create layers
        this.createLayers();
    }

    /**
     * Create the layered structure.
     */
    createLayers() {
        // Clear container
        this.container.innerHTML = '';

        // Create page container (2000x2000 default size)
        this.pageContainer = document.createElement('div');
        this.pageContainer.className = 'page-container';
        this.pageContainer.style.position = 'relative';
        this.pageContainer.style.width = '2000px';
        this.pageContainer.style.height = '2000px';
        this.pageContainer.style.background = '#ffffff';

        // Layer 0: HTML elements (text and images)
        this.htmlLayer = document.createElement('div');
        this.htmlLayer.className = 'html-layer';
        this.htmlLayer.style.position = 'absolute';
        this.htmlLayer.style.top = '0';
        this.htmlLayer.style.left = '0';
        this.htmlLayer.style.width = '100%';
        this.htmlLayer.style.height = '100%';
        this.htmlLayer.style.pointerEvents = 'none'; // Read-only

        // Layer 1: Canvas for drawings
        this.canvas = document.createElement('canvas');
        this.canvas.className = 'drawing-layer';
        this.canvas.width = 2000;
        this.canvas.height = 2000;
        this.canvas.style.position = 'absolute';
        this.canvas.style.top = '0';
        this.canvas.style.left = '0';
        this.canvas.style.pointerEvents = 'none'; // Read-only
        this.ctx = this.canvas.getContext('2d');

        // Assemble layers
        this.pageContainer.appendChild(this.htmlLayer);
        this.pageContainer.appendChild(this.canvas);
        this.container.appendChild(this.pageContainer);
    }

    /**
     * Render a complete page with all its elements.
     * @param {Object} pageData The page data from the server
     */
    renderPage(pageData) {
        this.currentPage = pageData;

        // Clear all layers
        this.htmlLayer.innerHTML = '';
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);

        if (!pageData || !pageData.elements) {
            console.warn('No page data or elements to render');
            return;
        }

        // Sort elements by zIndex (lowest first)
        const sortedElements = [...pageData.elements].sort((a, b) => a.zIndex - b.zIndex);

        // Separate elements by type
        const textElements = [];
        const imageElements = [];
        const drawingElements = [];

        for (const element of sortedElements) {
            switch (element['@type']) {
                case 'TEXT':
                    textElements.push(element);
                    break;
                case 'IMAGE':
                    imageElements.push(element);
                    break;
                case 'DRAWING':
                    drawingElements.push(element);
                    break;
            }
        }

        // Render text and image elements to HTML layer
        for (const element of textElements) {
            this.renderTextElement(element);
        }
        for (const element of imageElements) {
            this.renderImageElement(element);
        }

        // Render drawing elements to canvas
        // Use setTimeout to ensure canvas rendering happens in a separate task
        // This helps with Vivaldi's rendering pipeline
        setTimeout(() => {
            for (const element of drawingElements) {
                this.renderDrawingElement(element);
            }
            // Force canvas update
            this.forceCanvasUpdate();
        }, 0);

        console.log(`Rendered page: ${pageData.name} with ${sortedElements.length} elements`);
    }

    /**
     * Render a text element as HTML.
     * @param {Object} element The text element
     */
    renderTextElement(element) {
        const { id, x, y, width, height, htmlContent, zIndex } = element;

        if (!htmlContent) {
            return;
        }

        // Create text div
        const textDiv = document.createElement('div');
        textDiv.id = `element-${id}`;
        textDiv.className = 'text-element';
        textDiv.style.position = 'absolute';
        textDiv.style.left = `${x}px`;
        textDiv.style.top = `${y}px`;
        textDiv.style.width = `${width}px`;
        textDiv.style.height = `${height}px`;
        textDiv.style.zIndex = zIndex || 0;
        textDiv.style.overflow = 'hidden';
        textDiv.style.wordWrap = 'break-word';

        // Insert HTML content (preserves formatting)
        textDiv.innerHTML = htmlContent;

        // Force rendering by setting opacity and triggering reflow
        textDiv.style.opacity = '0';
        this.htmlLayer.appendChild(textDiv);

        // Force reflow before making visible
        void textDiv.offsetHeight;
        textDiv.style.opacity = '1';
    }

    /**
     * Render an image element as HTML.
     * @param {Object} element The image element
     */
    renderImageElement(element) {
        const { id, x, y, width, height, filename, zIndex } = element;

        const imageData = this.images.get(filename);
        if (!imageData) {
            console.debug('Image not yet loaded:', filename);
            return;
        }

        // Create image element
        const img = document.createElement('img');
        img.id = `element-${id}`;
        img.className = 'image-element';
        img.style.position = 'absolute';
        img.style.left = `${x}px`;
        img.style.top = `${y}px`;
        img.style.width = `${width}px`;
        img.style.height = `${height}px`;
        img.style.zIndex = zIndex || 0;
        img.src = imageData;

        this.htmlLayer.appendChild(img);
    }

    /**
     * Render a drawing element (freehand paths) to canvas.
     * @param {Object} element The drawing element
     */
    renderDrawingElement(element) {
        const { x, y, paths } = element;

        if (!paths || paths.length === 0) {
            return;
        }

        // Render each path with the element's offset
        for (const path of paths) {
            this.renderPath(path, x, y);
        }
    }

    /**
     * Render a single drawing path.
     * @param {Object} path The path to render
     * @param {number} offsetX The x offset of the drawing element
     * @param {number} offsetY The y offset of the drawing element
     */
    renderPath(path, offsetX = 0, offsetY = 0) {
        const { points, color, strokeWidth } = path;

        if (!points || points.length < 2) {
            return;
        }

        this.ctx.strokeStyle = color || '#000000';
        this.ctx.lineWidth = strokeWidth || 2;
        this.ctx.lineCap = 'round';
        this.ctx.lineJoin = 'round';

        this.ctx.beginPath();
        // Apply offset to first point
        this.ctx.moveTo(points[0].x + offsetX, points[0].y + offsetY);

        // Apply offset to all subsequent points
        for (let i = 1; i < points.length; i++) {
            this.ctx.lineTo(points[i].x + offsetX, points[i].y + offsetY);
        }

        this.ctx.stroke();
    }

    /**
     * Add an image to the cache and re-render.
     * @param {string} filename The image filename
     * @param {string} base64Data The base64-encoded image data
     * @param {string} mimeType The image MIME type
     */
    addImage(filename, base64Data, mimeType) {
        const dataUrl = `data:${mimeType};base64,${base64Data}`;
        this.images.set(filename, dataUrl);

        console.log('Image cached:', filename);

        // Re-render the page with the new image
        if (this.currentPage) {
            this.renderPage(this.currentPage);
        }
    }

    /**
     * Set the zoom level.
     * @param {number} scale The scale factor (1.0 = 100%)
     */
    setZoom(scale) {
        this.scale = Math.max(0.1, Math.min(5.0, scale));
        this.pageContainer.style.transform = `scale(${this.scale})`;
        this.pageContainer.style.transformOrigin = '0 0';
    }

    /**
     * Get the current zoom level.
     * @returns {number} The current scale factor
     */
    getZoom() {
        return this.scale;
    }

    /**
     * Clear all layers.
     */
    clear() {
        this.htmlLayer.innerHTML = '';
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        this.images.clear();
        this.currentPage = null;
    }

    /**
     * Force a reflow to ensure rendering happens even in background tabs.
     * Triggers a layout recalculation by reading offsetHeight.
     */
    forceReflow() {
        // Reading offsetHeight forces the browser to recalculate layout
        void this.pageContainer.offsetHeight;

        // Force repaint by toggling a transform
        this.htmlLayer.style.transform = 'translateZ(0)';
        requestAnimationFrame(() => {
            this.htmlLayer.style.transform = '';
        });

        // Also force repaint on all text elements
        const textElements = this.htmlLayer.querySelectorAll('.text-element');
        textElements.forEach(el => {
            void el.offsetHeight;
        });

        // Force canvas update for Vivaldi
        this.forceCanvasUpdate();
    }

    /**
     * Force canvas rendering update (especially needed for Vivaldi browser).
     */
    forceCanvasUpdate() {
        // Save and restore canvas state to trigger re-render
        this.ctx.save();
        this.ctx.restore();

        // Force a minimal draw operation to trigger GPU update
        const imgData = this.ctx.getImageData(0, 0, 1, 1);
        this.ctx.putImageData(imgData, 0, 0);

        // Also try toggling canvas style to force repaint
        const prevOpacity = this.canvas.style.opacity;
        this.canvas.style.opacity = '0.999';
        requestAnimationFrame(() => {
            this.canvas.style.opacity = prevOpacity || '1';
        });
    }
}

// Make available globally
window.PageRenderer = PageRenderer;
