import numpy as np
from kivy.app import App
from kivy.clock import Clock
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.togglebutton import ToggleButton

class EngineRPMApp(App):
    def build(self):
        self.stroke_type = 2
        self.is_recording = False
        
        main_layout = BoxLayout(orientation='vertical', padding=20, spacing=15)
        self.label_status = Label(text='ГОТОВ К ИЗМЕРЕНИЮ', font_size='16sp', color=(0.6, 0.6, 0.6, 1), size_hint=(1, 0.08))
        self.label_freq = Label(text='0.0 Гц', font_size='22sp', color=(0.2, 0.7, 1, 1), size_hint=(1, 0.1))
        self.label_rpm = Label(text='0', font_size='72sp', bold=True, color=(0.2, 0.9, 0.3, 1), size_hint=(1, 0.35))
        label_unit = Label(text='ОБ/МИН (RPM)', font_size='18sp', bold=True, color=(0.8, 0.8, 0.8, 1), size_hint=(1, 0.08))
        
        grid_stroke = GridLayout(cols=2, spacing=10, size_hint=(1, 0.12))
        self.btn_2t = ToggleButton(text='2-Тактный (2T)\n1 имп/оборот', state='down', group='stroke', font_size='14sp')
        self.btn_4t = ToggleButton(text='4-Тактный (4T)\n1 имп/2 оборота', group='stroke', font_size='14sp')
        self.btn_2t.bind(on_press=lambda x: setattr(self, 'stroke_type', 2))
        self.btn_4t.bind(on_press=lambda x: setattr(self, 'stroke_type', 4))
        grid_stroke.add_widget(self.btn_2t)
        grid_stroke.add_widget(self.btn_4t)
        
        self.btn_start = Button(text='ЗАПУСК', font_size='26sp', bold=True, size_hint=(1, 0.2), background_color=(0.1, 0.6, 0.9, 1))
        self.btn_start.bind(on_press=self.toggle_measure)
        
        main_layout.add_widget(self.label_status)
        main_layout.add_widget(self.label_rpm)
        main_layout.add_widget(label_unit)
        main_layout.add_widget(self.label_freq)
        main_layout.add_widget(grid_stroke)
        main_layout.add_widget(self.btn_start)
        return main_layout

    def toggle_measure(self, instance):
        if not self.is_recording:
            self.is_recording = True
            self.btn_start.text = 'СТОП'
            self.btn_start.background_color = (0.9, 0.2, 0.2, 1)
            self.label_status.text = 'ИЗМЕРЕНИЕ...'
            Clock.schedule_interval(self.update_rpm, 0.3)
        else:
            self.is_recording = False
            self.btn_start.text = 'ЗАПУСК'
            self.btn_start.background_color = (0.1, 0.6, 0.9, 1)
            self.label_status.text = 'ПАУЗА'
            Clock.unschedule(self.update_rpm)

    def update_rpm(self, dt):
        sample_rate = 44100
        t = np.linspace(0, 0.3, int(sample_rate * 0.3), endpoint=False)
        simulated_signal = np.sin(2 * np.pi * 95 * t) + np.random.normal(0, 0.4, len(t))
        fft_vals = np.abs(np.fft.rfft(simulated_signal))
        freqs = np.fft.rfftfreq(len(simulated_signal), 1.0 / sample_rate)
        
        rpm_factor = 60.0 if self.stroke_type == 2 else 120.0
        min_freq, max_freq = 1000 / rpm_factor, 14000 / rpm_factor
        
        valid_idx = np.where((freqs >= min_freq) & (freqs <= max_freq))[0]
        if len(valid_idx) > 0:
            peak_freq = freqs[valid_idx[np.argmax(fft_vals[valid_idx])]]
            calculated_rpm = peak_freq * rpm_factor
            self.label_freq.text = f'Частота пика: {round(peak_freq, 1)} Гц'
            self.label_rpm.text = f'{int(calculated_rpm)}'

if __name__ == '__main__':
    EngineRPMApp().run()
      
