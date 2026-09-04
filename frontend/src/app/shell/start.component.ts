import { ChangeDetectionStrategy, Component, EventEmitter, Output } from '@angular/core';

@Component({
  selector: 'app-start',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './start.component.html',
  styleUrl: './start.component.scss',
})
export class StartComponent {
  @Output() readonly chooseLogin = new EventEmitter<void>();
  @Output() readonly chooseRegister = new EventEmitter<void>();
}
